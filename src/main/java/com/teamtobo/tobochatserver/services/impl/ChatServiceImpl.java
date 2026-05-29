package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.events.ForwardMessageEvent;
import com.teamtobo.tobochatserver.dtos.events.MemberInboxUpdateEvent;
import com.teamtobo.tobochatserver.dtos.events.UnreadMessageUpdateEvent;
import com.teamtobo.tobochatserver.dtos.events.UserInboxUpdateEvent;
import com.teamtobo.tobochatserver.dtos.payloads.MessageReactionPayload;
import com.teamtobo.tobochatserver.dtos.response.*;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.entities.MessageReaction;
import com.teamtobo.tobochatserver.entities.documents.LatestMessage;
import com.teamtobo.tobochatserver.entities.enums.ReactionType;
import com.teamtobo.tobochatserver.entities.enums.UnreadUpdateType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.entities.enums.MessageStatus;
import com.teamtobo.tobochatserver.services.ChatService;
import com.teamtobo.tobochatserver.services.RoomService;
import com.teamtobo.tobochatserver.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {
    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<MessageReaction> messageReactionTable;
    private final DynamoDbTable<Message> messageTable;
    private final SocketIOServer socketIOServer;
    private final UserService userService;
    private final RoomService roomService;
    private final S3Presigner s3Presigner;
    private final ApplicationEventPublisher eventPublisher;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    @Override
    public MessageResponse getRoomMessage(String userId, String roomId, String messageId) {
        // TODO: Check xem user hiện tại (userId) có trong phòng này ko (integrity)
        Message message = messageTable.getItem(Key.builder()
                .partitionValue("ROOM#" + roomId)
                .sortValue("MSG#" + messageId)
                .build());

        if (message == null) return null;

        boolean isRevoked = message.getMessageStatus() == MessageStatus.REVOKED;
        return MessageResponse.builder()
                .id(messageId)
                .roomId(roomId)
                .user(userService.getUserProfile(message.getSenderId()))
                .attachments(isRevoked ? null : message.getAttachments())
                .content(isRevoked ? null : message.getContent())
                .messageStatus(message.getMessageStatus())
                .createdAt(message.getCreatedAt())
                .build();
    }

    @Override
    public Message getMessageById(String messageId, String roomId) {
        Message message = messageTable.getItem(r -> r.key(
                Key.builder()
                        .partitionValue("ROOM#" + roomId)
                        .sortValue("MSG#" + messageId)
                        .build()));

        if (message == null)
            throw new AppException(ErrorCode.MESSAGE_NOT_FOUND);
        return message;
    }

    @Override
    public MessageResponse getMessage(String messageId, String roomId) {
        Message message = getMessageById(messageId, roomId);

        if (message == null) throw new AppException(ErrorCode.MESSAGE_NOT_FOUND);

        return MessageResponse.builder()
                .id(messageId)
                .content(message.getContent())
                .attachments(message.getAttachments())
                .createdAt(message.getCreatedAt())
                .messageStatus(message.getMessageStatus())
                .action(message.getAction())
                .roomId(roomId)
                .messageType(message.getMessageType())
                .metadata(message.getMetadata())
                .roomId(roomId)
                .build();
    }

    @Override
    public Map<String, Message> getMessagesMapByIds(List<String> messageIds, String roomId) {
        if (messageIds == null || messageIds.isEmpty()) {
            return new HashMap<>();
        }

        // Loại bỏ các ID trùng lặp
        List<String> uniqueIds = messageIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Message> messageMap = new HashMap<>();

        int batchSize = 100;
        for (int i = 0; i < uniqueIds.size(); i += batchSize) {
            List<String> chunk = uniqueIds.subList(i, Math.min(uniqueIds.size(), i + batchSize));

            ReadBatch.Builder<Message> readBatchBuilder = ReadBatch.builder(Message.class)
                    .mappedTableResource(messageTable);

            chunk.forEach(id -> readBatchBuilder.addGetItem(Key.builder()
                    .partitionValue("ROOM#" + roomId)
                    .sortValue("MSG#" + id)
                    .build()));

            BatchGetResultPageIterable batchResults = enhancedClient.batchGetItem(r -> r.addReadBatch(readBatchBuilder.build()));

            // Đọc kết quả của lô hiện tại và map vào kết quả
            batchResults.resultsForTable(messageTable).forEach(message ->
                    messageMap.put(message.getSk().replace("MSG#", ""), message));
        }

        return messageMap;
    }

    @Override
    public PageResponse<MessageResponse> getMessages(
            String userId,
            String roomId,
            String cursor,
            int limit,
            String direction // "before" | "after" | "both"
    ) {
        String pk = "ROOM#" + roomId;
        List<Message> items = new ArrayList<>();

        // Dùng riêng cho trường hợp "both"
        boolean hasMoreOlderBoth = false;
        boolean hasMoreNewerBoth = false;
        Map<String, AttributeValue> lastEvaluatedKeyOriginal = null;

        if ("both".equals(direction) && cursor != null && !cursor.isEmpty()) {
            Key key = Key.builder().partitionValue(pk).sortValue(cursor).build();
            int halfLimit = limit / 2;

            // 1.1 Fetch AFTER (Tin mới hơn, tiến về tương lai)
            QueryEnhancedRequest afterReq = QueryEnhancedRequest.builder()
                    .queryConditional(QueryConditional.sortGreaterThan(key))
                    .scanIndexForward(true)
                    .limit(halfLimit)
                    .build();
            Page<Message> afterPage = messageTable.query(afterReq).stream().findFirst().orElse(null);

            // 1.2 Fetch BEFORE (Tin cũ hơn + lấy chính cursor hiện tại)
            QueryEnhancedRequest beforeReq = QueryEnhancedRequest.builder()
                    .queryConditional(QueryConditional.sortLessThanOrEqualTo(key))
                    .scanIndexForward(false)
                    .limit(halfLimit + 1)
                    .build();
            Page<Message> beforePage = messageTable.query(beforeReq).stream().findFirst().orElse(null);

            // 1.3 Gộp data
            if (afterPage != null) {
                List<Message> afterItems = new ArrayList<>(afterPage.items());
                Collections.reverse(afterItems);
                items.addAll(afterItems);
                hasMoreNewerBoth = afterPage.lastEvaluatedKey() != null && !afterPage.lastEvaluatedKey().isEmpty() && afterItems.size() == halfLimit;
            }

            if (beforePage != null) {
                List<Message> beforeItems = new ArrayList<>(beforePage.items());
                items.addAll(beforeItems);
                hasMoreOlderBoth = beforePage.lastEvaluatedKey() != null && !beforePage.lastEvaluatedKey().isEmpty() && beforeItems.size() == (halfLimit + 1);
            }

        } else {
            QueryConditional queryConditional;
            if (cursor != null && !cursor.isEmpty()) {
                Key key = Key.builder().partitionValue(pk).sortValue(cursor).build();
                if ("before".equals(direction)) {
                    queryConditional = QueryConditional.sortLessThan(key);
                } else {
                    queryConditional = QueryConditional.sortGreaterThan(key);
                }
            } else {
                queryConditional = QueryConditional.keyEqualTo(Key.builder().partitionValue(pk).build());
            }

            boolean scanForward = !"before".equals(direction);
            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .scanIndexForward(scanForward)
                    .limit(limit)
                    .build();

            Page<Message> messagePage = messageTable.query(request).stream().findFirst().orElse(null);

            if (messagePage != null) {
                // Chỉ lấy raw items, phần map để lại phía dưới làm chung
                items = new ArrayList<>(messagePage.items());
                lastEvaluatedKeyOriginal = messagePage.lastEvaluatedKey();
            }
        }

        // Lọc bỏ null và các record không phải là message
        items = items.stream()
                .filter(Objects::nonNull)
                .filter(msg -> msg.getSk() != null && msg.getSk().startsWith("MSG#"))
                .collect(Collectors.toList());

        // Lọc id của các replied message cho batch get
        List<String> repliedMessageIds = items.stream()
                .filter(msg -> msg.getMessageStatus() != MessageStatus.REVOKED && msg.getReplyTo() != null)
                .map(Message::getReplyTo)
                .collect(Collectors.toList());

        Map<String, Message> messageMap = getMessagesMapByIds(repliedMessageIds, roomId);

        // Thêm userId của message gốc cho batch get
        List<String> userIds = new ArrayList<>();
        List<String> messageIds = new ArrayList<>();

        for (Message message : items) {
            userIds.add(message.getSenderId());
            messageIds.add(message.getSk().replace("MSG#", ""));
        }

        // Thêm luôn userId của replied message
        messageMap.forEach((k, v) -> userIds.add(v.getSenderId()));

        // Batch get user
        Map<String, UserResponse> userResponseMap = userService.getUsersMapByIds(userIds);

        // Batch get reaction
        Map<String, MessageReaction> messageReactionMap = getMyReactionsMapByIds(userId, messageIds);

        // DTO mapping
        List<MessageResponse> messageResponses = items.stream()
                .filter(msg -> !msg.getDeletedByUserIds().contains(userId))
                .map(msg -> {
                    String messageId = msg.getSk().replace("MSG#", "");
                    boolean isRevoked = msg.getMessageStatus() == MessageStatus.REVOKED;
                    UserResponse userResponse = userResponseMap.get(msg.getSenderId());

                    Message repliedMessage = messageMap.getOrDefault(msg.getReplyTo(), null);
                    MessageResponse repliedMessageResponse = repliedMessage != null ? MessageResponse.builder()
                            .user(userResponseMap.getOrDefault(repliedMessage.getSenderId(), null))
                            .id(msg.getReplyTo())
                            .content(repliedMessage.getContent())
                            .attachments(repliedMessage.getAttachments())
                            .roomId(roomId)
                            .build() : null;

                    MessageReaction myReaction = messageReactionMap.getOrDefault(messageId, null);

                    return MessageResponse.builder()
                            .id(messageId)
                            .roomId(roomId)
                            // Tin nhắn đã thu hồi ko cần trả về content và replyTo
                            .content(isRevoked ? null : msg.getContent())
                            .replyTo(!isRevoked ? repliedMessageResponse : null)
                            .createdAt(msg.getCreatedAt())
                            .user(userResponse)
                            .attachments(isRevoked ? null : msg.getAttachments())
                            .messageStatus(msg.getMessageStatus())
                            .reactionsSummary(msg.getReactionsSummary())
                            .myReactions(myReaction != null ? myReaction.getReactions() : null)
                            .messageType(msg.getMessageType())
                            .action(msg.getAction())
                            .metadata(msg.getMetadata())
                            .build();
                }).collect(Collectors.toList());

        // 3. XỬ LÝ CURSOR
        String nextCursor = null;
        String prevCursor = null;

        if (!items.isEmpty()) {
            String first = items.get(0).getSk();
            String last = items.get(items.size() - 1).getSk();

            if ("both".equals(direction) && cursor != null && !cursor.isEmpty()) {
                prevCursor = hasMoreNewerBoth ? first : null;
                nextCursor = hasMoreOlderBoth ? last : null;
            } else {
                if (cursor == null || cursor.isEmpty()) {
                    nextCursor = last;
                } else if ("before".equals(direction)) {
                    prevCursor = first;
                    nextCursor = last;
                } else {
                    prevCursor = last;
                    nextCursor = first;
                    Collections.reverse(messageResponses);
                }

                // Phát hiện hết dữ liệu
                if (lastEvaluatedKeyOriginal == null || lastEvaluatedKeyOriginal.isEmpty() || items.size() < limit) {
                    if ("before".equals(direction) || cursor == null || cursor.isEmpty()) {
                        nextCursor = null;
                    } else {
                        prevCursor = null;
                    }
                }
            }
        }

        eventPublisher.publishEvent(
                new UnreadMessageUpdateEvent(userId, roomId, UnreadUpdateType.RESET)
        );

        return new PageResponse<>(messageResponses, nextCursor, prevCursor);
    }

    @Override
    public void addReaction(String userId, String roomId, String messageId, ReactionType reactionType) {
        String reactionPk = "MSG#" + messageId;
        String reactionSk = "REACTION#" + userId;
        Key reactionKey = Key.builder().partitionValue(reactionPk).sortValue(reactionSk).build();

        // Check xem User đã react chưa
        MessageReaction existingReaction = messageReactionTable.getItem(r -> r.key(reactionKey));

        if (existingReaction != null && existingReaction.getReactions().contains(reactionType.name())) {
            log.info("Reaction {} đã tồn tại trong tin nhắn {}", reactionType.name(), messageId);
            return;
        }

        if (existingReaction == null) {
            existingReaction = MessageReaction.builder()
                    .pk(reactionPk)
                    .sk(reactionSk)
                    .build();
        }

        existingReaction.getReactions().add(reactionType.name());
        messageReactionTable.putItem(existingReaction);

        String messagePk = "ROOM#" + roomId;
        String messageSk = "MSG#" + messageId;

        // Atomic update, đọc và cập nhật trong Dynamodb
        String updateExpression = "SET #summary.#type = if_not_exists(#summary.#type, :zero) + :inc";
        Map<String, String> expressionNames = Map.of(
                "#summary", "reactionsSummary",
                "#type", reactionType.name()
        );
        Map<String, AttributeValue> expressionValues = Map.of(
                ":inc", AttributeValue.builder().n("1").build(),
                ":zero", AttributeValue.builder().n("0").build()
        );

        UpdateItemRequest updateCounterRequest = UpdateItemRequest.builder()
                .tableName(messageTable.tableName())
                .key(Map.of(
                        "pk", AttributeValue.builder().s(messagePk).build(),
                        "sk", AttributeValue.builder().s(messageSk).build()
                ))
                .updateExpression(updateExpression)
                .expressionAttributeNames(expressionNames)
                .expressionAttributeValues(expressionValues)
                .build();
        try {
            dynamoDbClient.updateItem(updateCounterRequest);

            MessageReactionPayload reactionPayload = MessageReactionPayload.builder()
                    .userId(userId)
                    .roomId(roomId)
                    .messageId(messageId)
                    .reactionType(reactionType)
                    .build();

            socketIOServer.getRoomOperations(roomId)
                    .sendEvent("reaction_added", reactionPayload);
        } catch (software.amazon.awssdk.services.dynamodb.model.DynamoDbException e) {
            // Nếu Parent Map chưa tồn tại
            if (e.getMessage().contains("document path provided in the update expression is invalid")) {

                // Khởi tạo
                Map<String, AttributeValue> initialMap = Map.of(
                        reactionType.name(), AttributeValue.builder().n("1").build()
                );

                String initExpression = "SET #summary = :newMap";

                UpdateItemRequest initRequest = UpdateItemRequest.builder()
                        .tableName(messageTable.tableName())
                        .key(Map.of(
                                "pk", AttributeValue.builder().s(messagePk).build(),
                                "sk", AttributeValue.builder().s(messageSk).build()
                        ))
                        .updateExpression(initExpression)
                        .expressionAttributeNames(Map.of("#summary", "reactionsSummary"))
                        .expressionAttributeValues(Map.of(
                                ":newMap", AttributeValue.builder().m(initialMap).build()
                        ))
                        .build();

                dynamoDbClient.updateItem(initRequest);
            } else {
                throw e;
            }
        }
    }

    @Override
    public Map<String, MessageReaction> getMyReactionsMapByIds(String userId, List<String> messageIds) {
        if (messageIds == null || messageIds.isEmpty()) {
            return new HashMap<>();
        }

        // Loại bỏ các ID trùng lặp
        List<String> uniqueIds = messageIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, MessageReaction> messageReactionMap = new HashMap<>();

        int batchSize = 100;
        for (int i = 0; i < uniqueIds.size(); i += batchSize) {
            List<String> chunk = uniqueIds.subList(i, Math.min(uniqueIds.size(), i + batchSize));

            ReadBatch.Builder<MessageReaction> readBatchBuilder = ReadBatch.builder(MessageReaction.class)
                    .mappedTableResource(messageReactionTable);

            chunk.forEach(id -> readBatchBuilder.addGetItem(Key.builder()
                    .partitionValue("MSG#" + id)
                    .sortValue("REACTION#" + userId)
                    .build()));

            BatchGetResultPageIterable batchResults = enhancedClient.batchGetItem(r -> r.addReadBatch(readBatchBuilder.build()));

            // Đọc kết quả của lô hiện tại và map vào kết quả
            batchResults.resultsForTable(messageReactionTable)
                    .forEach(messageReaction -> messageReactionMap
                            .put(messageReaction.getPk().replace("MSG#", ""), messageReaction));
        }

        return messageReactionMap;
    }

    @Override
    public PageResponse<MessageReactionResponse> getMessageReactions(String messageId, String roomId, String cursor, int limit) {
        String pk = "MSG#" + messageId;

        Key searchKey = Key.builder().partitionValue(pk).sortValue("REACTION#").build();
        QueryEnhancedRequest.Builder requestBuilder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.sortBeginsWith(searchKey))
                .limit(limit);

        if (cursor != null && !cursor.isEmpty()) {
            requestBuilder.exclusiveStartKey(Map.of(
                    "pk", AttributeValue.builder().s(pk).build(),
                    "sk", AttributeValue.builder().s(cursor).build()
            ));
        }

        Page<MessageReaction> page = messageReactionTable.query(requestBuilder.build()).stream().findFirst().orElse(null);
        if (page == null || page.items().isEmpty()) return new PageResponse<>(Collections.emptyList(), null, null);

        // Thu thập các UserId
        List<String> userIds = page.items().stream()
                .map(r -> r.getSk().replace("REACTION#", ""))
                .toList();

        Map<String, UserResponse> userProfileMap = userService.getUsersMapByIds(userIds);

        // Lắp ghép dữ liệu
        List<MessageReactionResponse> responses = page.items().stream()
                .map(reaction -> {
                    String uid = reaction.getSk().replace("REACTION#", "");
                    return MessageReactionResponse.builder()
                            .user(userProfileMap.getOrDefault(
                                    uid,
                                    UserResponse.builder().id(uid).name("Người dùng Tobo").build()))
                            .reactions(reaction.getReactions())
                            .build();
                })
                .collect(Collectors.toList());

        String nextCursor = (page.lastEvaluatedKey() != null) ? page.lastEvaluatedKey().get("sk").s() : null;
        return new PageResponse<>(responses, nextCursor, null);
    }

    // Lấy tin nhắn mới nhất từ góc nhìn của người dùng
    @Override
    public MessageResponse getLatestMessage(String userId, String roomId) {
        try {
            String pk = "ROOM#" + roomId;

            Key searchKey = Key.builder()
                    .partitionValue(pk)
                    .sortValue("MSG#")
                    .build();

            QueryConditional queryConditional = QueryConditional.sortBeginsWith(searchKey);

            Map<String, AttributeValue> lastEvaluatedKey = null;

            do { // Loop đến tin nhắn hợp lệ nếu tin nhắn mới nhất bị xoá
                QueryEnhancedRequest.Builder builder = QueryEnhancedRequest.builder()
                        .queryConditional(queryConditional)
                        .scanIndexForward(false) // mới nhất -> cũ nhất
                        .limit(20); // có thể chỉnh 20–50

                if (lastEvaluatedKey != null) {
                    builder.exclusiveStartKey(lastEvaluatedKey);
                }

                Page<Message> page = messageTable.query(builder.build()).iterator().next();

                for (Message msg : page.items()) {

                    // skip nếu user đã xoá
                    if (isDeletedForUser(msg, userId)) {
                        continue;
                    }

                    // xử lý message hợp lệ đầu tiên
                    return buildMessageResponse(msg);
                }

                lastEvaluatedKey = page.lastEvaluatedKey();

            } while (lastEvaluatedKey != null);

            // không còn message hợp lệ
            return null;

        } catch (Exception e) {
            log.error("Lỗi khi lấy tin nhắn mới nhất phòng {}: {}", roomId, e.getMessage());
            return null;
        }
    }

    // Lấy tin nhắn mới nhất của phòng
    @Override
    public MessageResponse getRoomLatestMessage(String roomId) {
        try {
            String pk = "ROOM#" + roomId;

            Key searchKey = Key.builder()
                    .partitionValue(pk)
                    .sortValue("MSG#")
                    .build();

            QueryConditional queryConditional = QueryConditional.sortBeginsWith(searchKey);

            QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                    .queryConditional(queryConditional)
                    .scanIndexForward(false) // Sắp xếp từ mới nhất -> cũ nhất
                    .limit(1) // Chỉ cần lấy đúng 1 dòng đầu tiên
                    .build();

            Page<Message> page = messageTable.query(request).stream().findFirst().orElse(null);

            if (page != null && !page.items().isEmpty()) {
                Message message = page.items().get(0);
                boolean isRevoked = message.getMessageStatus() == MessageStatus.REVOKED;

                return MessageResponse.builder()
                        .id(message.getSk().replaceFirst("^MSG#", ""))
                        .roomId(roomId)
                        .user(userService.getUserProfile(message.getSenderId())) // Cần thiết cho buildLatestMessage
                        .content(isRevoked ? null : message.getContent())
                        .attachments(isRevoked ? null : message.getAttachments())
                        .messageStatus(message.getMessageStatus())
                        .createdAt(message.getCreatedAt())
                        .messageType(message.getMessageType())
                        .metadata(message.getMetadata())
                        .action(message.getAction())
                        .build();
            }

            return null; // Không có tin nhắn nào trong phòng

        } catch (Exception e) {
            log.error("Lỗi khi lấy tin nhắn mới nhất (tuyệt đối) của phòng {}: {}", roomId, e.getMessage());
            return null;
        }
    }

    @Override
    public LatestMessage buildLatestMessage(MessageResponse message) {
        int mediaSize = 0;
        int fileSize = 0;

        if (message.getAttachments() != null && !message.getAttachments().isEmpty()) {
            for (var attachment : message.getAttachments()) {
                String contentType = attachment.getContentType();

                if (contentType != null && (contentType.startsWith("image/") || contentType.startsWith("video/"))) {
                    mediaSize++;
                } else {
                    fileSize++;
                }
            }
        }

        return LatestMessage.builder()
                .roomId(message.getRoomId())
                .messageId(message.getId())
                .userId(message.getUser().getId())
                .messageType(message.getMessageType())
                .messageStatus(message.getMessageStatus())
                .metadata(message.getMetadata())
                .fileSize(fileSize)
                .mediaSize(mediaSize)
                .action(message.getAction())
                .content(message.getContent())
                .createdAt(message.getCreatedAt())
                .build();
    }

    @Override
    public MessageResponse buildMessageResponse(Message message) {
        boolean isRevoked = message.getMessageStatus() == MessageStatus.REVOKED;

        return MessageResponse.builder()
                .id(message.getSk().replace("MSG#", ""))
                .content(isRevoked ? null : message.getContent())
                .attachments(isRevoked ? null : message.getAttachments())
                .messageStatus(message.getMessageStatus())
                .roomId(message.getPk().replace("ROOM#", ""))
                .action(message.getAction())
                .createdAt(message.getCreatedAt())
                .replyTo(MessageResponse.builder().id(message.getReplyTo()).build())
                .metadata(message.getMetadata())
                .messageType(message.getMessageType())
                .build();
    }

    private boolean isDeletedForUser(Message msg, String userId) {
        return msg.getDeletedByUserIds() != null && msg.getDeletedByUserIds().contains(userId);
    }

    @Override
    public void revokeMessage(String userId, String roomId, String messageId) {
        try {
            String pk = "ROOM#" + roomId;

            QueryConditional queryConditional = QueryConditional.sortBeginsWith(
                    Key.builder()
                            .partitionValue(pk)
                            .sortValue("MSG#")
                            .build()
            );

            Message message = messageTable.query(r -> r.queryConditional(queryConditional))
                    .items()
                    .stream()
                    .filter(m -> m.getSk() != null && m.getSk().endsWith(messageId))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("Tin nhắn không tồn tại"));

            if (!message.getSenderId().equals(userId)) {
                throw new RuntimeException("Không có quyền thu hồi");
            }

            if (message.getMessageStatus() == MessageStatus.REVOKED) {
                return;
            }

            message.setMessageStatus(MessageStatus.REVOKED);
            messageTable.updateItem(message);

            socketIOServer.getRoomOperations("room:" + roomId)
                    .sendEvent("message_revoked",
                            Map.of(
                                    "messageId", messageId,
                                    "roomId", roomId
                            ));

            MessageResponse roomLatestMessage = getRoomLatestMessage(roomId);
            MessageResponse myLatestMessage = getLatestMessage(userId, roomId);

            // Nếu là tin nhắn mới nhất dưới góc nhìn của người dùng hiện tại thì cập nhật lại inbox
            if (Objects.equals(messageId, myLatestMessage.getId()))
                eventPublisher.publishEvent(new UserInboxUpdateEvent(userId, roomId));

            // Nếu không phải là tin nhắn mới nhất của nhóm thì không cập nhật inbox cho các thành viên
            if(!Objects.equals(roomLatestMessage.getId(), messageId)) return;

            eventPublisher.publishEvent(
                    new MemberInboxUpdateEvent(roomId, userId, buildMessageResponse(message), true)
            );
        } catch (RuntimeException e) {
            log.error("Lỗi nghiệp vụ revoke message: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("Lỗi hệ thống khi thu hồi tin nhắn: {}", e.getMessage());
            throw new RuntimeException("Không thể thu hồi tin nhắn lúc này", e);
        }
    }

    @Override
    public void forwardMessages(String userId, String fromRoomId, List<String> messageIds, List<String> toRoomIds) {
        eventPublisher.publishEvent(
                new ForwardMessageEvent(userId, fromRoomId, toRoomIds, messageIds)
        );
    }

    @Override
    public PresignedUrlResponse generateAttachmentPresignedUrl(String fileName, String roomId, String contentType) {
        String objectKey = "temp-drafts/" + roomId + "/" + UUID.randomUUID() + "-" + fileName;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(objectKey)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(10)) // URL có hạn trong 10 phút
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        // Lấy chuỗi URL thô chứa chữ ký
        String rawUploadUrl = presignedRequest.url().toString();

        // Cắt bỏ phần chữ ký (từ dấu ? trở đi) để lấy URL thực tế
        // Lưu ý: Dùng "\\?" vì trong Java Regex, dấu ? là ký tự đặc biệt
        String cleanFileUrl = rawUploadUrl.split("\\?")[0];

        return PresignedUrlResponse.builder()
                .uploadUrl(rawUploadUrl)
                .fileUrl(cleanFileUrl)
                .build();
    }

    @Override
    public void deleteMessage(String messageId, String roomId, String userId) {
        try {
            String pk = "ROOM#" + roomId;
            String sk = "MSG#" + messageId;

            Key key = Key.builder()
                    .partitionValue(pk)
                    .sortValue(sk)
                    .build();

            Message message = messageTable.getItem(r -> r.key(key));
            if (message == null) return;

            List<String> deletedList = message.getDeletedByUserIds();

            if (deletedList == null) {
                deletedList = new ArrayList<>();
            } else {
                deletedList = new ArrayList<>(deletedList); // clone
            }

            if (deletedList.contains(userId)) return;
            deletedList.add(userId);

            // Lấy trước tin nhắn mới nhất lúc chưa cập nhật
            MessageResponse latestMessage = getLatestMessage(userId, roomId);

            message.setDeletedByUserIds(deletedList);
            messageTable.updateItem(message);

            socketIOServer.getRoomOperations(userId)
                    .sendEvent("delete_message", MessageResponse.builder()
                            .id(messageId)
                            .roomId(roomId)
                            .build());

            // Chỉ cập nhật inbox khi xoá tin nhắn mới nhất (dưới góc nhìn của người dùng hiện tại)
            if (!Objects.equals(messageId, latestMessage.getId())) return;

            //  Cập nhật cho chính tôi, người khác ko bị ảnh hưởng
            eventPublisher.publishEvent(new UserInboxUpdateEvent(userId, roomId));
        } catch (Exception e) {
            throw new RuntimeException("Không thể xoá tin nhắn", e);
        }
    }
}
