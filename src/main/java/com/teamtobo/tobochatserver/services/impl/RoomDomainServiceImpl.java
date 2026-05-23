package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.events.RoomUpdateEvent;
import com.teamtobo.tobochatserver.dtos.events.SystemMessageCreateEvent;
import com.teamtobo.tobochatserver.dtos.payloads.NewRoomPayload;
import com.teamtobo.tobochatserver.dtos.request.MemberUpdateRequest;
import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.dtos.request.RoomUpdateRequest;
import com.teamtobo.tobochatserver.dtos.response.*;
import com.teamtobo.tobochatserver.entities.*;
import com.teamtobo.tobochatserver.entities.enums.*;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.repositories.RoomNodeRepository;
import com.teamtobo.tobochatserver.services.*;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

import java.time.Instant;
import java.util.*;
import java.util.stream.Stream;

@Slf4j
@Service
@AllArgsConstructor
public class RoomDomainServiceImpl implements RoomDomainService {
    private final SocketIOServer socketIOServer;

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Room> roomTable;
    private final DynamoDbTable<RoomMember> roomMemberTable;

    private final UserService userService;
    private final RoomService roomService;
    private final RoomMemberService roomMemberService;
    private final ChatService chatService;

    private final DynamoDbTable<GroupAcceptRequest> groupAcceptRequestTable;
    private final DynamoDbTable<GroupPendingRequest> groupPendingRequestTable;

    private final ApplicationEventPublisher eventPublisher;

    private final RoomNodeRepository roomNodeRepository;

    // Tạo nhóm và add member
    @Override
    public RoomResponse createRoom(String userId, RoomCreateRequest request, RoomType roomType) {
        List<String> members = prepareMembers(userId, request);
        RoomResponse response = null;

        switch (roomType) {
            case DM -> response = createDMRoom(userId, members);
            case GROUP -> response = createGroupRoom(userId, request, members);
        }
        return response;
    }

    @Override
    public void updateRoomSettings(String roomId, RoomUpdateRequest request) {
        Room room = roomService.getRoomById(roomId, false);

        if (request.getAllowSendMessage() != null) { // Cho phép gửi tin nhắn vào phòng
            room.setAllowSendMessage(request.getAllowSendMessage());
        }

        if (request.getAllowAddMember() != null) { // Cho phép thêm thành viên vào phòng
            room.setAllowAddMember(request.getAllowAddMember());
        }

        if (request.getAllowUpdateMetadata() != null) { // Cho phép cập nhật thông tin phòng
            room.setAllowUpdateMetadata(request.getAllowUpdateMetadata());
        }

        if(request.getApproveMember() != null) { // Phê duyệt khi vào phòng
            room.setApproveMember(request.getApproveMember());
        }

        roomTable.updateItem(room);
    }

    @Override
    public void approveMember(String roomId, String adminId, String targetUserId, boolean accept) {
        Room room = roomService.getRoomById(roomId, true);

        //phải là group
        if (room.getRoomType() != RoomType.GROUP) {
            throw new AppException(ErrorCode.ROOM_INVALID);
        }

        //group có bật duyệt không
        if (!room.isApproveMember()) {
            throw new AppException(ErrorCode.ROOM_NOT_REQUIRE_APPROVAL);
        }

        String pk = "ROOM#" + roomId;
        String sk = "PENDING#" + targetUserId;

        GroupPendingRequest pending = groupPendingRequestTable.getItem(
                Key.builder().partitionValue(pk).sortValue(sk).build()
        );

        if (pending == null) {
            throw new AppException(ErrorCode.PENDING_REQUEST_NOT_FOUND);
        }

        //xoá pending trước
        groupPendingRequestTable.deleteItem(
                Key.builder().partitionValue(pk).sortValue(sk).build()
        );

        room.setPendingCount(room.getPendingCount() - 1);
        roomTable.updateItem(room);

        //reject
        if (!accept) return;

        //tránh add trùng
        if (isMember(roomId, targetUserId)) return;

        User targetUser = userService.getUserById(targetUserId);

        //tránh tạo accept request trùng
        GroupAcceptRequest existed = groupAcceptRequestTable.getItem(
                Key.builder()
                        .partitionValue("USER#" + targetUserId)
                        .sortValue("ROOM_ACCEPT#" + roomId)
                        .build()
        );

        if (existed != null) return;

        if (targetUser.isAllowAutoAddToGroup()) {
            addMember(roomId, targetUserId, room.getRoomName());
            socketIOServer.getRoomOperations(targetUserId)
                    .sendEvent("new_room", RoomResponse.builder()
                            .id(roomId)
                            .roomName(room.getRoomName())
                            .roomType(room.getRoomType())
                            .avatarUrl(room.getAvatarUrl())
                            .allowAddMember(room.isAllowAddMember())
                            .allowSendMessage(room.isAllowSendMessage())
                            .allowUpdateMetadata(room.isAllowUpdateMetadata())
                            .approveMember(room.isApproveMember())
                            .memberCount(room.getMemberCount())
                            // Nếu phòng đã có tin nhắn trước đó
                            .latestMessage(chatService.getLatestMessage(targetUserId, roomId))
                            // TODO: chỉ lấy được pending count nếu là admin hoặc vice admin
                            .build());
        } else {
            createGroupAcceptRequest(roomId, adminId, targetUserId, room.getRoomName());
        }
    }

    // Add member khi đã tạo nhóm
    @Override
    public List<FriendResponse> addMemberToGroup(String roomId, String inviterId, List<String> targetUserIds) {
        Room room = roomService.getRoomById(roomId, true);
        RoomMember inviter = getMember(roomId, inviterId);
        List<FriendResponse> friendResponseList = new ArrayList<>();

        for (String targetUserId : targetUserIds) {
            // Không thêm nếu đã thành viên trong phòng
            if (isMember(roomId, targetUserId)) continue;

            // Nếu không phải bạn bè của người mời
            validateFriend(inviterId, targetUserId);
            User targetUser = userService.getUserById(targetUserId);
            MemberStatus memberStatus = handleAddMember(room, inviter, targetUser, targetUserId);

            FriendResponse friendResponse = FriendResponse.builder()
                    .id(targetUserId)
                    .memberStatus(memberStatus)
                    .build();

            friendResponseList.add(friendResponse);
        }

        // Trả về cho người thêm trạng thái của từng bạn bè khi đã thêm
        return friendResponseList;
    }
    @Override
    public void updateMember(String roomId, String memberId, MemberUpdateRequest request) {
        RoomMember targetMember = getMember(roomId, memberId);
        targetMember.setRole(request.getMemberRole());
        targetMember.setUpdatedAt(Instant.now().toString());
        roomMemberTable.updateItem(targetMember);
    }

    @Override
    public void removeMember(String roomId, String removerId, String memberId) {
        RoomMember remover = getMember(roomId, removerId);
        RoomMember target = getMember(roomId, memberId);
        User targetUser = userService.getUserById(memberId); // Lấy tên của người dùng bị xoá gán vào tin nhắn hệ thống

        if (remover.getRole() == MemberRole.MEMBER) {
            throw new AppException(ErrorCode.INVALID_PERMISSION);
        }

        if (remover.getRole() == MemberRole.VICE_ADMIN && target.getRole() != MemberRole.MEMBER) {
            throw new AppException(ErrorCode.INVALID_PERMISSION);
        }

        roomMemberTable.deleteItem(target);

        // Sự kiện cho người bị kick
        socketIOServer.getRoomOperations(memberId)
                .sendEvent("self_removed", roomId);

        // Sự kiện cho các thành viên khác trong nhóm để cập nhật phòng
        socketIOServer.getRoomOperations("room:" + roomId)
                .sendEvent("member_removed", memberId);

        // Tạo tin nhắn hệ thống
        eventPublisher.publishEvent(
                new SystemMessageCreateEvent(
                        roomId,
                        removerId,
                        SystemAction.MEMBER_REMOVED,
                        Map.of("removedMemberId", memberId,
                                "removedMemberName", targetUser.getName())
                )
        );
    }

    @Override
    public void disbandGroup(String roomId) {
        List<RoomMember> members = roomMemberService.findAllRoomMembers(roomId);

        TransactWriteItemsEnhancedRequest.Builder txBuilder = TransactWriteItemsEnhancedRequest.builder();

        txBuilder.addDeleteItem(roomTable, Key.builder()
                .partitionValue("ROOM#" + roomId)
                .sortValue("METADATA")
                .build());

        for (RoomMember member : members) {
            txBuilder.addDeleteItem(roomMemberTable, member);
        }

        try {
            enhancedClient.transactWriteItems(txBuilder.build());

            for (RoomMember member: members) {
                socketIOServer.getRoomOperations(Helper.normalizeId(member.getSk()))
                        .sendEvent("room_disband", roomId);
            }
        } catch (Exception e) {
            throw new AppException(ErrorCode.ROOM_DISBAND_ERROR);
        }
    }

    @Override
    public LeaveCheckResponse checkLeave(String userId, String roomId) {
        Room room = roomService.getRoomById(roomId, false);
        RoomMember member = getMember(roomId, userId);

        if(member.getRole() == MemberRole.ADMIN && room.getMemberCount() != 1)
            return LeaveCheckResponse.builder()
                    .canLeave(false)
                    .reason("TRANSFER_REQUIRED")
                    .build();

        return LeaveCheckResponse.builder()
                .canLeave(true)
                .build();
    }

    @Override
    public void leaveGroup(String userId, String roomId, String newAdminId) {
        RoomMember member = getMember(roomId, userId);
        Room room = roomService.getRoomById(roomId, true);

        if (room.getMemberCount() == 1) {
            disbandGroup(roomId);
            return;
        }

        room.setMemberCount(room.getMemberCount() - 1);
        TransactWriteItemsEnhancedRequest.Builder txBuilder = TransactWriteItemsEnhancedRequest.builder();

        txBuilder.addUpdateItem(roomTable, room);

        if (member.getRole() == MemberRole.ADMIN) {
            if (newAdminId == null)
                throw new AppException(ErrorCode.REQUIRE_EXCHANGER);

            if (newAdminId.equals(userId))
                throw new AppException(ErrorCode.REQUIRE_EXCHANGER);

            RoomMember newAdmin = getMember(roomId, newAdminId);

            if (newAdmin == null)
                throw new AppException(ErrorCode.NOT_IN_ROOM);

            newAdmin.setRole(MemberRole.ADMIN); // thay thế admin

            txBuilder.addUpdateItem(roomMemberTable, newAdmin);
        }

        txBuilder.addDeleteItem(roomMemberTable, member);
        try {
            enhancedClient.transactWriteItems(txBuilder.build());

            // Tạo tin nhắn hệ thống
            eventPublisher.publishEvent(
                    new SystemMessageCreateEvent(
                            roomId,
                            userId,
                            SystemAction.MEMBER_LEFT,
                            null
                    )
            );
        } catch (Exception e) {
            throw new AppException(ErrorCode.CANNOT_LEAVE_ROOM);
        }
    }

    @Override
    public String getOrCreateDMRoom(String userId, String otherId) {
        List<String> members = Stream.of(userId, otherId)
                .distinct()
                .sorted()
                .toList();

        if (members.size() != 2) {
            throw new AppException(ErrorCode.ROOM_INVALID);
        }

        String roomId = members.get(0) + "_" + members.get(1);

        Room existed = roomService.getRoomById(roomId, true);
        if (existed != null) return roomId;

        // gọi lại logic createRoom cũ
        createRoom(userId,
                RoomCreateRequest.builder()
                        .memberIds(List.of(otherId))
                        .build(),
                RoomType.DM
        );

        return roomId;
    }
    private RoomResponse createDMRoom(String userId, List<String> members) { // Tạo phòng DM
        if (members.size() != 2) {
            throw new AppException(ErrorCode.ROOM_INVALID);
        }

        List<String> sorted = members.stream().sorted().toList();
        String roomId = sorted.get(0) + "_" + sorted.get(1);

        Room existed = roomService.getRoomById(roomId, true);
        if (existed != null) {
            // Nếu phòng đã tồn tại thì cập nhật lại memberStatus thành ACTIVE
            for(String memberId : members) {
                RoomMember member = roomMemberService.getMemberById(memberId, roomId);
                if (member.getStatus() == InboxStatus.PENDING)
                    socketIOServer.getRoomOperations(memberId)
                            .sendEvent("pending_inbox_updated", roomMemberService.getRoomMetadata(memberId, roomId));

                log.info("Room {} existed, updating inbox status from {} to ACTIVE for user {}", roomId, member.getStatus(), memberId);
                member.setStatus(InboxStatus.ACTIVE);

                roomMemberTable.updateItem(member);
            }

            return RoomResponse.builder()
                    .id(roomId)
                    .roomType(existed.getRoomType())
                    .createdAt(existed.getCreatedAt())
                    .build();
        }

        String now = Instant.now().toString();
        String pk = "ROOM#" + roomId;

        String otherId = sorted.stream()
                .filter(id -> !id.equals(userId))
                .findFirst()
                .orElseThrow();

        // check friend
        FriendStatus friendStatus = userService.getFriendStatus(userId, otherId);

        InboxStatus senderStatus = InboxStatus.ACTIVE;
        InboxStatus receiverStatus =
                (friendStatus == FriendStatus.FRIEND)
                        ? InboxStatus.ACTIVE
                        : InboxStatus.PENDING;

        TransactWriteItemsEnhancedRequest.Builder tx =
                TransactWriteItemsEnhancedRequest.builder();

        // 1. Room metadata
        tx.addPutItem(roomTable, Room.builder()
                .pk(pk)
                .roomType(RoomType.DM)
                .createdAt(now)
                .updatedAt(now)
                .build());

        // 2. Sender
        tx.addPutItem(roomMemberTable, buildMember(
                roomId,
                userId,
                "Direct Message",
                MemberRole.MEMBER,
                now,
                senderStatus
        ));

        // 3. Receiver
        tx.addPutItem(roomMemberTable, buildMember(
                roomId,
                otherId,
                "Direct Message",
                MemberRole.MEMBER,
                now,
                receiverStatus
        ));

        enhancedClient.transactWriteItems(tx.build());

        // Cập nhật real time cho người bên kia (otherId)
        RoomResponse otherRoomMetadata = roomMemberService.getRoomMetadata(otherId, roomId);
        otherRoomMetadata.setLatestMessage(chatService.getLatestMessage(otherId, roomId));
        socketIOServer.getRoomOperations(otherId)
                .sendEvent("new_room", NewRoomPayload.builder()
                        .room(otherRoomMetadata)
                        .inboxStatus(receiverStatus)
                        .build());

        RoomResponse myRoomMetadata = roomMemberService.getRoomMetadata(userId, roomId);
        myRoomMetadata.setLatestMessage(chatService.getLatestMessage(userId, roomId));
        socketIOServer.getRoomOperations(userId)
                .sendEvent("new_room", NewRoomPayload.builder()
                        .room(myRoomMetadata)
                        .inboxStatus(senderStatus)
                        .build());

        return myRoomMetadata;
    }

    private RoomResponse createGroupRoom(String userId, RoomCreateRequest request, List<String> members) {

        // validate
        if (members.size() < 2) {
            throw new AppException(ErrorCode.GROUP_SIZE_INVALID);
        }

        if (request.getRoomName() == null || request.getRoomName().trim().isEmpty()) {
            throw new AppException(ErrorCode.ROOM_NAME_REQUIRED);
        }

        String roomId = UUID.randomUUID().toString();
        String now = Instant.now().toString();

        // tạo room + creator trước
        saveRoomToDynamoDB(
                userId,
                roomId,
                request.getRoomName(),
                List.of(userId),
                now
        );

        Room room = roomService.getRoomById(roomId, true);
        RoomMember creator = getMember(roomId, userId);

        // Tạo tin nhắn hệ thống
        eventPublisher.publishEvent(
                new SystemMessageCreateEvent(
                        roomId,
                        userId,
                        SystemAction.ROOM_CREATED,
                        null
                )
        );

        // add members còn lại
        for (String memberId : members) {
            if (memberId.equals(userId)) continue;

            validateFriend(userId, memberId);

            if (isMember(roomId, memberId)) continue;

            User targetUser = userService.getUserById(memberId);

            handleAddMember(room, creator, targetUser, memberId);
        }

        return RoomResponse.builder()
                .id(roomId)
                .roomName(room.getRoomName())
                .roomType(room.getRoomType())
                .avatarUrl(room.getAvatarUrl())
                .allowAddMember(room.isAllowAddMember())
                .allowSendMessage(room.isAllowSendMessage())
                .allowUpdateMetadata(room.isAllowUpdateMetadata())
                .approveMember(room.isApproveMember())
                .build();
    }

    private List<String> prepareMembers(String userId, RoomCreateRequest request) {
        List<String> memberIds = new ArrayList<>(request.getMemberIds());
        memberIds.add(userId);

        return memberIds.stream().distinct().toList();
    }

    // Lưu Room (metadata) + RoomMember
    private void saveRoomToDynamoDB(
            String userId,
            String roomId,
            String roomName,
            List<String> memberIds,
            String now
    ) {
        String pk = "ROOM#" + roomId;

        TransactWriteItemsEnhancedRequest.Builder txBuilder =
                TransactWriteItemsEnhancedRequest.builder();

        Room roomMetadata = Room.builder()
                .pk(pk)
                .roomName(roomName)
                .allowAddMember(true)
                .allowUpdateMetadata(true)
                .allowSendMessage(true)
                .approveMember(false)
                .roomType(RoomType.GROUP)
                .createdAt(now)
                .updatedAt(now)
                .memberCount(memberIds.size())
                .pendingCount(0)
                .build();

        // Lưu metadata trước
        txBuilder.addPutItem(roomTable, roomMetadata);

        for (String memberId : memberIds) {
            boolean isAdmin = Objects.equals(memberId, userId);

            String sk = "MEMBER#" + memberId;

            RoomMember member = RoomMember.builder()
                    .pk(pk)
                    .sk(sk)
                    .role(isAdmin ? MemberRole.ADMIN : MemberRole.MEMBER)
                    .status(InboxStatus.ACTIVE)
                    .roomName(roomName)
                    .lastActivityAt(now)
                    .roomType(RoomType.GROUP)
                    .createdAt(now)
                    .updatedAt(now)
                    .statusTime("STATUS#ACTIVE#TIME#" + now)
                    .addedBy(userId)
                    .build();

            txBuilder.addPutItem(roomMemberTable, member);
        }

        try {
            enhancedClient.transactWriteItems(txBuilder.build());
        } catch (Exception e) {
            throw new AppException(ErrorCode.ROOM_CREATE_ERROR);
        }
    }

    private RoomMember buildMember(
            String roomId,
            String userId,
            String roomName,
            MemberRole role,
            String now,
            InboxStatus status
    ) {
        return RoomMember.builder()
                .pk("ROOM#" + roomId)
                .sk("MEMBER#" + userId)
                .role(role)
                .status(status)
                .roomName(roomName)
                .lastActivityAt(now)
                .createdAt(now)
                .updatedAt(now)
                .statusTime("STATUS#" + status + "#TIME#" + now)
                .build();
    }

    // Kiểm tra và thêm các thành viên khác vào nhóm (tạo RoomMember hoặc GroupPendingRequest, GroupAcceptRequest)
    // Chỉ sử dụng cho roomType = GROUP
    private MemberStatus handleAddMember(Room room, RoomMember inviter, User targetUser, String targetUserId) {
        String roomId = room.getPk().replace("ROOM#", "");
        String inviterId = inviter.getMemberId();
        String roomName = room.getRoomName();

        // Nhóm có xét duyệt không?
        if (room.isApproveMember()) {
            // Inviter có phải Admin không?
            if (inviter.getRole() != MemberRole.ADMIN) {
                // Thành viên thường thêm người thì phải chờ duyệt (Pending)
                createGroupPendingRequest(roomId, inviterId, targetUserId, roomName);
                return MemberStatus.PENDING;
            }
        }

        // B có cho phép tự động thêm vào group?
        if (targetUser.isAllowAutoAddToGroup()) {
            addMember(roomId, targetUserId, roomName);

            // Gửi socket để cập nhật lập tức inbox của người được add
            // Chỉ gửi nếu người đó cho tự động thêm vào group
            socketIOServer.getRoomOperations(targetUserId)
                    .sendEvent("new_room", NewRoomPayload.builder()
                            .room(RoomResponse.builder()
                                    .id(roomId)
                                    .roomName(room.getRoomName())
                                    .roomType(room.getRoomType())
                                    .avatarUrl(room.getAvatarUrl())
                                    .allowAddMember(room.isAllowAddMember())
                                    .allowSendMessage(room.isAllowSendMessage())
                                    .allowUpdateMetadata(room.isAllowUpdateMetadata())
                                    .approveMember(room.isApproveMember())
                                    .memberCount(room.getMemberCount())
                                    // Nếu phòng đã có tin nhắn trước đó
                                    .latestMessage(chatService.getLatestMessage(targetUserId, roomId))
                                    // TODO: chỉ lấy được pending count nếu là admin hoặc vice admin
                                    .build())
                            .inboxStatus(InboxStatus.ACTIVE)
                            .build());

            socketIOServer.getRoomOperations("room:" + roomId)
                    .sendEvent("new_member", RoomMemberResponse.builder()
                            .id(targetUserId)
                            .roomId(roomId)
                            .role(MemberRole.MEMBER)
                            .member(UserResponse.builder()
                                    .id(targetUserId)
                                    .name(targetUser.getName())
                                    .avatarUrl(targetUser.getAvatarUrl())
                                    .build())
                            .build());

            // Tạo tin nhắn hệ thống
            eventPublisher.publishEvent(
                    new SystemMessageCreateEvent(
                            roomId,
                            inviterId,
                            SystemAction.MEMBER_ADDED,
                            Map.of("newMemberId", targetUserId,
                                    "newMemberName", targetUser.getName())) // Thông tin lịch sử, chấp nhận không nhất quán
            );

            return MemberStatus.ADDED;
        }

        createGroupAcceptRequest(roomId, inviterId, targetUserId, roomName);
        return MemberStatus.SENT;
    }

    @Override
    public RoomMember getMember(String roomId, String userId) {
        RoomMember member = roomMemberTable.getItem(
                Key.builder()
                        .partitionValue("ROOM#" + roomId)
                        .sortValue("MEMBER#" + userId)
                        .build()
        );

        if (member == null) {
            throw new AppException(ErrorCode.NOT_IN_ROOM);
        }
        return member;
    }

    private boolean isMember(String roomId, String userId) {
        return roomMemberTable.getItem(
                Key.builder()
                        .partitionValue("ROOM#" + roomId)
                        .sortValue("MEMBER#" + userId)
                        .build()
        ) != null;
    }

    private void validateFriend(String inviterId, String targetUserId) {
        if (userService.getFriendStatus(inviterId, targetUserId) != FriendStatus.FRIEND) {
            throw new AppException(ErrorCode.ONLY_FRIEND_CAN_ADD);
        }
    }

    private void addMember(String roomId, String userId, String roomName) {
        TransactWriteItemsEnhancedRequest.Builder tx =
                TransactWriteItemsEnhancedRequest.builder();
        String now = Instant.now().toString();
        Room room = roomService.getRoomById(roomId, false);

        room.setMemberCount(room.getMemberCount() + 1);

        tx.addUpdateItem(roomTable, room);

        RoomMember member = RoomMember.builder()
                .pk("ROOM#" + roomId)
                .sk("MEMBER#" + userId)
                .role(MemberRole.MEMBER)
                .status(InboxStatus.ACTIVE)
                .roomType(RoomType.GROUP)
                .roomName(roomName)
                .lastActivityAt(now)
                .createdAt(now)
                .updatedAt(now)
                .statusTime("STATUS#ACTIVE#TIME#" + now)
                .build();

        tx.addPutItem(roomMemberTable, member);

        try {
            enhancedClient.transactWriteItems(tx.build());
        } catch (Exception e) {
            throw new AppException(ErrorCode.ADD_MEMBER_ERROR);
        }
    }

    private void createGroupAcceptRequest(String roomId, String inviterId, String targetUserId, String roomName) {
        groupAcceptRequestTable.putItem(
                GroupAcceptRequest.builder()
                        .pk("USER#" + targetUserId)
                        .sk("ROOM_ACCEPT#" + roomId)
                        .roomId(roomId)
                        .groupRequestPk("ROOM_ACCEPT#" + roomId)
                        .receiverSk("USER#" + targetUserId)
                        .inviterId(inviterId)
                        .roomName(roomName)
                        .build()
        );
    }

    private void createGroupPendingRequest(String roomId, String inviterId, String targetUserId, String roomName) {
        TransactWriteItemsEnhancedRequest.Builder tx =
                TransactWriteItemsEnhancedRequest.builder();

        Room room = roomService.getRoomById(roomId, true);
        room.setPendingCount(room.getPendingCount() + 1);
        tx.addUpdateItem(roomTable, room);

        GroupPendingRequest groupPendingRequest = GroupPendingRequest.builder()
                .pk("ROOM#" + roomId)
                .sk("PENDING#" + targetUserId)
                .roomId(roomId)
                .userId(targetUserId)
                .requesterId(inviterId)
                .roomName(roomName)
                .build();

        tx.addPutItem(groupPendingRequestTable, groupPendingRequest);

        try {
            enhancedClient.transactWriteItems(tx.build());
        } catch (Exception e) {
            throw new AppException(ErrorCode.GROUP_PENDING_ERROR);
        }
    }

    @Override
    public void updateRoomAvatar(String userId, String roomId, String avatarUrl) {
        Room room = roomService.getRoomById(roomId, false);
        if (room == null) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }

        room.setAvatarUrl(avatarUrl);
        roomTable.updateItem(room);

        // Tạo tin nhắn hệ thống
        eventPublisher.publishEvent(
                new SystemMessageCreateEvent(
                        roomId,
                        userId,
                        SystemAction.ROOM_AVATAR_CHANGED,
                        null)
        );

        eventPublisher.publishEvent(
                new RoomUpdateEvent(
                        roomId,
                        null,
                        avatarUrl
                )
        );
    }

    @Override
    public void updateRoomName(String userId, String roomId, String roomName) {
        Room room = roomService.getRoomById(roomId, false);

        if (roomName == null || roomName.isBlank()) {
            throw new AppException(ErrorCode.ROOM_NAME_INVALID);
        }

        room.setRoomName(roomName);
        roomTable.updateItem(room);

        // Tạo tin nhắn hệ thống
        eventPublisher.publishEvent(
                new SystemMessageCreateEvent(
                        roomId,
                        userId,
                        SystemAction.ROOM_NAME_CHANGED,
                        Map.of("newRoomName", roomName)
                )
        );

        eventPublisher.publishEvent(
                new RoomUpdateEvent(
                        roomId,
                        roomName,
                        null
                )
        );
    }


    //----------------------

    @Override
    public void addMemberNeo4j(String roomId, String userId) {
        log.info("[Neo4j] Thực hiện gán cạnh JOINED từ User {} tới Room {} (Tự động xóa trạng thái cũ nếu có)", userId, roomId);
        roomNodeRepository.addMember(roomId, userId);
    }

    @Override
    public void createGroupAcceptRequestNeo4j(String roomId, String inviterId, String targetUserId) {
        log.info("[Neo4j] Thực hiện gán cạnh INVITED từ User {} tới Room {} - Người mời: {} (Tự động xóa trạng thái cũ nếu có)", targetUserId, roomId, inviterId);
        roomNodeRepository.createSent(roomId, inviterId, targetUserId);
    }

    @Override
    public void createGroupPendingRequestNeo4j(String roomId, String inviterId, String targetUserId) {
        log.info("[Neo4j] Thực hiện gán cạnh PENDING_APPROVAL từ User {} tới Room {} - Người mời duyệt: {} (Tự động xóa trạng thái cũ nếu có)", targetUserId, roomId, inviterId);
        roomNodeRepository.createPending(roomId, inviterId, targetUserId);
    }

    @Override
    public List<String> getJoinedRoomIdsNeo4j(String userId) {
        log.info("[Neo4j] Lấy danh sách ID phòng mà User {} đã tham gia", userId);
        return roomNodeRepository.findRoomIdsByUserId(userId);
    }

    @Override
    public MemberStatus getMemberStatusNeo4j(String roomId, String userId) {
        log.info("[Neo4j] Lấy trạng thái thành viên của User {} trong Room {}", userId, roomId);

        String statusStr = roomNodeRepository.getMemberStatus(roomId, userId);

        // Nếu không tìm thấy bất kỳ cạnh nào (trả về null hoặc chuỗi trống)
        if (statusStr == null || statusStr.isBlank()) {
            return MemberStatus.NOT_IN_GROUP;
        }

        try {
            return MemberStatus.valueOf(statusStr);
        } catch (IllegalArgumentException e) {
            return MemberStatus.NOT_IN_GROUP;
        }
    }

    @Override
    public void deleteMemberRelationshipNeo4j(String roomId, String userId) {
        log.info("[Neo4j] Thực hiện xóa mọi quan hệ giữa User {} và Room {}", userId, roomId);
        roomNodeRepository.deleteRelation(roomId, userId);
    }

}
