package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.events.SystemMessageCreateEvent;
import com.teamtobo.tobochatserver.dtos.response.*;
import com.teamtobo.tobochatserver.entities.GroupAcceptRequest;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
import com.teamtobo.tobochatserver.entities.enums.MemberRole;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.entities.enums.SystemAction;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.*;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupAcceptRequestServiceImpl implements GroupAcceptRequestService {
    private final SocketIOServer socketIOServer;

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<GroupAcceptRequest> requestTable;
    private final DynamoDbTable<RoomMember> roomMemberTable;
    private final DynamoDbTable<Room> roomTable;

    private final RoomService roomService;
    private final ChatService chatService;
    private final UserService userService;
    private final RoomMemberService roomMemberService;

    private final ApplicationEventPublisher eventPublisher;

    // Danh sách người được mời chưa chấp nhận
    @Override
    public PageResponse<GroupSentRequestResponse> getSentRequests(String roomId, String cursor, int limit) {
        String gsiPartitionKey = "ROOM_ACCEPT#" + roomId;
        DynamoDbIndex<GroupAcceptRequest> index = requestTable.index("GSI_GroupSentRequest");
        // pk: roomId
        // sk: userId

        QueryEnhancedRequest.Builder builder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(gsiPartitionKey)))
                .limit(limit);

        // Xử lý Pagination Cursor (Giả sử cursor là receiverSk - ID người nhận)
        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = new HashMap<>();

            // 1. Khoá của GSI (Phải khớp chính xác tên thuộc tính trong DynamoDB)
            exclusiveStartKey.put("groupRequestPk", AttributeValue.builder().s(gsiPartitionKey).build());
            exclusiveStartKey.put("receiverSk", AttributeValue.builder().s(cursor).build());

            // 2. Khoá của Bảng gốc (Base Table)
            exclusiveStartKey.put("pk", AttributeValue.builder().s("ROOM#" + roomId).build());
            exclusiveStartKey.put("sk", AttributeValue.builder().s(cursor).build());

            builder.exclusiveStartKey(exclusiveStartKey);
        }

        // Thực thi Query trên Index
        SdkIterable<Page<GroupAcceptRequest>> results = index.query(builder.build());
        Iterator<Page<GroupAcceptRequest>> iterator = results.iterator();

        // Xử lý an toàn trường hợp không có dữ liệu
        if (!iterator.hasNext()) {
            return PageResponse.<GroupSentRequestResponse>builder().items(List.of()).build();
        }

        Page<GroupAcceptRequest> firstPage = iterator.next();

        if (firstPage.items().isEmpty()) {
            return PageResponse.<GroupSentRequestResponse>builder().items(List.of()).build();
        }

        // Lấy cursor cho trang tiếp theo (dựa trên GSI Sort Key)
        String nextCursor = null;
        if (firstPage.lastEvaluatedKey() != null && firstPage.lastEvaluatedKey().containsKey("receiverSk")) {
            nextCursor = firstPage.lastEvaluatedKey().get("receiverSk").s();
        }

        return PageResponse.<GroupSentRequestResponse>builder()
                .items(firstPage.items().stream().map(
                        i -> GroupSentRequestResponse.builder()
                                .user(userService.getUserProfile(Helper.normalizeId(i.getPk())))
                                .inviter(userService.getUserProfile(i.getInviterId()))
                                .build()
                ).toList())
                .nextCursor(nextCursor)
                .build();
    }

    // TODO: xử lý phân trang
    @Override
    public PageResponse<GroupAcceptRequestResponse> getInvites(
            String userId,
            String cursor,
            int limit
    ) {

        String pk = "USER#" + userId;

        QueryEnhancedRequest query = QueryEnhancedRequest.builder()
                .queryConditional(
                        QueryConditional.sortBeginsWith(
                                Key.builder()
                                        .partitionValue(pk)
                                        .sortValue("ROOM_ACCEPT#")
                                        .build()
                        )
                )
                .limit(limit)
                .build();

        List<GroupAcceptRequestResponse> items = requestTable.query(query)
                .items()
                .stream()
                .map(item -> GroupAcceptRequestResponse.builder()
                        .roomId(item.getRoomId())
                        .roomName(item.getRoomName())
                        .avatarUrl(item.getAvatarUrl())
                        .inviter(userService.getUserProfile(item.getInviterId()))
                        .build())
                .collect(Collectors.toList());

        return PageResponse.<GroupAcceptRequestResponse>builder()
                .items(items)
                .nextCursor(null)
                .build();
    }

    // Thêm response type để cập nhật trực tiếp trên UI mà ko cần fetch lại
    @Override
    public RoomResponse respondInvite(String userId, String roomId, boolean accepted) {
        if (!accepted) return null;

        String pk = "USER#" + userId;
        String sk = "ROOM_ACCEPT#" + roomId;

        Key key = Key.builder()
                .partitionValue(pk)
                .sortValue(sk)
                .build();

        GroupAcceptRequest request = requestTable.getItem(key);

        if (request == null) {
            throw new AppException(ErrorCode.REQUEST_NOT_FOUND);
        }

        Room room = roomService.getRoomById(roomId, true);

        //check đã là member chưa
        RoomMember existing = roomMemberTable.getItem(
                Key.builder()
                        .partitionValue("ROOM#" + roomId)
                        .sortValue("MEMBER#" + userId)
                        .build()
        );

        if (existing != null) {
            requestTable.deleteItem(key);
            return null;
        }

        TransactWriteItemsEnhancedRequest.Builder tx = TransactWriteItemsEnhancedRequest.builder();

        // Tăng memberCount
        room.setMemberCount(room.getMemberCount() + 1);
        tx.addUpdateItem(roomTable, room);
        String now = Instant.now().toString();

        RoomMember member = RoomMember.builder()
                .pk("ROOM#" + roomId)
                .sk("MEMBER#" + userId)
                .role(MemberRole.MEMBER)
                .roomType(RoomType.GROUP)
                .status(InboxStatus.ACTIVE)
                .roomName(request.getRoomName())
                .lastActivityAt(now)
                .createdAt(now)
                .updatedAt(now)
                .statusTime("STATUS#ACTIVE#TIME#" + now)
                .build();

        tx.addPutItem(roomMemberTable, member);

        tx.addDeleteItem(requestTable, key);

        try {
            enhancedClient.transactWriteItems(tx.build());
            UserResponse newMember = userService.getUserProfile(userId);

            List<RoomMember> roomMembers = roomMemberService.findAllRoomMembers(roomId);
            for(RoomMember rm: roomMembers) {
                socketIOServer.getRoomOperations(rm.getMemberId())
                            .sendEvent("new_member", RoomMemberResponse.builder()
                                    .id(userId)
                                    .roomId(roomId)
                                    .role(MemberRole.MEMBER)
                                    .member(newMember)
                                    .build());
            }

            // Tạo tin nhắn hệ thống
            eventPublisher.publishEvent(
                    new SystemMessageCreateEvent(
                            roomId,
                            userId,
                            SystemAction.GROUP_INVITE_ACCEPTED,
                            null
                    )
            );

            return RoomResponse.builder()
                    .id(roomId)
                    .roomName(room.getRoomName())
                    .roomType(room.getRoomType())
                    .memberCount(room.getMemberCount())
                    .latestMessage(chatService.buildLatestMessage(chatService.getRoomLatestMessage(roomId)))
                    .avatarUrl(room.getAvatarUrl())
                    .build();
        } catch (Exception e) {
            throw new AppException(ErrorCode.GROUP_INVITE_HANDLE_FAILED);
        }
    }
}