package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.events.MemberUpdateEvent;
import com.teamtobo.tobochatserver.dtos.events.RoomUpdateEvent;
import com.teamtobo.tobochatserver.dtos.events.SystemMessageCreateEvent;
import com.teamtobo.tobochatserver.dtos.payloads.NewRoomPayload;
import com.teamtobo.tobochatserver.dtos.payloads.RoomUpdatePayload;
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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
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

        eventPublisher.publishEvent(
                new RoomUpdateEvent(
                        roomId,
                        RoomUpdatePayload.builder()
                                .allowSendMessage(request.getAllowSendMessage())
                                .allowAddMember(request.getAllowAddMember())
                                .allowUpdateMetadata(request.getAllowUpdateMetadata())
                                .approveMember(request.getApproveMember())
                                .build()
                )
        );

        eventPublisher.publishEvent(
                new MemberUpdateEvent(roomId, room)
        );
    }

    @Override
    public void approveMember(String roomId, String adminId, String targetUserId, boolean accept) {
        Room room = roomService.getRoomById(roomId, true);

        // Id người chủ động thêm
        String inviterId = roomNodeRepository.getPendingInviterId(roomId, targetUserId);

        if (inviterId == null) {
            throw new AppException(ErrorCode.PENDING_REQUEST_NOT_FOUND);
        }

        if (room.getPendingCount() > 0) {
            room.setPendingCount(room.getPendingCount() - 1);
            roomTable.updateItem(room);
        }

        if (!accept) {
            deleteMemberRelationshipNeo4j(roomId, targetUserId);
            return;
        }

        // Tránh add trùng nếu đã là member
        if (isMember(roomId, targetUserId)) return;

        User targetUser = userService.getUserById(targetUserId);

        // Tránh tạo accept request trùng
        MemberStatus currentStatus = getMemberStatusNeo4j(roomId, targetUserId);
        if (currentStatus == MemberStatus.SENT) return;

        // Xử lý theo setting cá nhân của User
        if (targetUser.isAllowAutoAddToGroup()) {
            addMember(roomId, targetUserId);

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
                                    .latestMessage(chatService.buildLatestMessage(
                                            chatService.getRoomLatestMessage(roomId)))
                                    .build())
                            .inboxStatus(InboxStatus.ACTIVE)
                            .build());

            // Tạo tin nhắn hệ thống
            eventPublisher.publishEvent(
                    new SystemMessageCreateEvent(
                            roomId,
                            adminId,
                            SystemAction.MEMBER_APPROVED,
                            Map.of("approvedMemberId", targetUserId,
                                    "approvedMemberName", targetUser.getName()))
            );
        } else {
            createGroupAcceptRequestNeo4j(roomId, inviterId, targetUserId);
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
    public void updateMemberRole(String roomId, String userId, String memberId, MemberUpdateRequest request) {
        RoomMember targetMember = getMember(roomId, memberId);
        targetMember.setRole(request.getMemberRole());
        targetMember.setUpdatedAt(Instant.now().toString());
        roomMemberTable.updateItem(targetMember);

        UserResponse userResponse = userService.getUserProfile(memberId);

        // Tạo tin nhắn hệ thống
        eventPublisher.publishEvent(
                new SystemMessageCreateEvent(
                        roomId,
                        userId,
                        SystemAction.MEMBER_ROLE_UPDATED,
                        Map.of("updatedMemberId", memberId,
                                "updatedMemberName", userResponse.getName(),
                                "newRole", request.getMemberRole().name())
                )
        );
    }

    @Override
    public void removeMember(String roomId, String removerId, String memberId) {
        RoomMember remover = getMember(roomId, removerId);
        RoomMember target = getMember(roomId, memberId);
        User targetUser = userService.getUserById(memberId); // Lấy tên của người dùng bị xoá gán vào tin nhắn hệ thống

        // Cấp trên mới xoá được cấp dưới
        if (!remover.getRole().isHigherThan(target.getRole())) {
            throw new AppException(ErrorCode.INVALID_PERMISSION);
        }

        roomMemberTable.deleteItem(target);

        // Xoá cạnh quan hệ trong Neo4j
        deleteMemberRelationshipNeo4j(roomId, memberId);

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

            // Xoá cạnh quan hệ trong Neo4j
            deleteMemberRelationshipNeo4j(roomId, userId);

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
        otherRoomMetadata.setLatestMessage(chatService.buildLatestMessage(chatService.getLatestMessage(otherId, roomId)));
        socketIOServer.getRoomOperations(otherId)
                .sendEvent("new_room", NewRoomPayload.builder()
                        .room(otherRoomMetadata)
                        .inboxStatus(receiverStatus)
                        .build());

        RoomResponse myRoomMetadata = roomMemberService.getRoomMetadata(userId, roomId);
        myRoomMetadata.setLatestMessage(chatService.buildLatestMessage(chatService.getLatestMessage(userId, roomId)));
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

        // Nhóm có xét duyệt không?
        if (room.isApproveMember()) {
            // Inviter có phải Admin không?
            if (inviter.getRole() != MemberRole.ADMIN) {
                // Thành viên thường thêm người thì phải chờ duyệt (Pending)
                createGroupPendingRequestNeo4j(roomId, inviterId, targetUserId);
                return MemberStatus.PENDING;
            }
        }

        // B có cho phép tự động thêm vào group?
        if (targetUser.isAllowAutoAddToGroup()) {
            addMember(roomId, targetUserId);

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
                                    .latestMessage(chatService.buildLatestMessage(
                                            chatService.getRoomLatestMessage(roomId)))
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

        createGroupAcceptRequestNeo4j(roomId, inviterId, targetUserId);
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

    // 1. Tạo RoomMember trong dynamoDB
    // 2. Lưu quan hệ vào Neo4j
    private void addMember(String roomId, String userId) {
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
                .roomName(room.getRoomName())
                .lastActivityAt(now)
                .createdAt(now)
                .updatedAt(now)
                .statusTime("STATUS#ACTIVE#TIME#" + now)
                .build();

        tx.addPutItem(roomMemberTable, member);

        try {
            enhancedClient.transactWriteItems(tx.build());

            // Ánh xạ lên Neo4j
            addMemberNeo4j(roomId, userId);
        } catch (Exception e) {
            throw new AppException(ErrorCode.ADD_MEMBER_ERROR);
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
                        RoomUpdatePayload.builder()
                                .newRoomAvatar(avatarUrl)
                                .build()
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
                        RoomUpdatePayload.builder()
                                .newRoomName(roomName)
                                .build()
                )
        );
    }


    // -----Neo4j services-----

    @Override
    public void addMemberNeo4j(String roomId, String userId) {
        roomNodeRepository.addMember(roomId, userId);
    }

    // Danh sách lời mời vào nhóm
    @Override
    public PageResponse<GroupAcceptRequestResponse> getAcceptRequests(String userId, String cursor, int limit) {
        int page = (cursor == null || cursor.isEmpty()) ? 0 : Integer.parseInt(cursor);
        Pageable pageable = PageRequest.of(page, limit);

        List<RoomNodeRepository.AcceptRequestData> requestDataList = roomNodeRepository.findAcceptRequestsByUserId(userId, pageable);

        boolean hasNext = requestDataList.size() > limit;
        List<RoomNodeRepository.AcceptRequestData> currentRequests = hasNext ? requestDataList.subList(0, limit) : requestDataList;

        if (currentRequests.isEmpty()) {
            return PageResponse.<GroupAcceptRequestResponse>builder().items(List.of()).build();
        }

        Set<String> inviterIdsToFetch = currentRequests.stream()
                .map(RoomNodeRepository.AcceptRequestData::inviterId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        Map<String, UserResponse> userProfileMap = userService.getUsersMapByIds(new ArrayList<>(inviterIdsToFetch));

        List<GroupAcceptRequestResponse> items = currentRequests.stream().map(req -> {
            Room room = roomService.getRoomById(req.roomId(), false);

            return GroupAcceptRequestResponse.builder()
                    .roomId(req.roomId())
                    .roomName(room != null ? room.getRoomName() : "Nhóm ToboChat")
                    .avatarUrl(room != null ? room.getAvatarUrl() : null)
                    .inviter(userProfileMap.get(req.inviterId())) // Gắn profile người mời
                    .build();
        }).toList();

        return PageResponse.<GroupAcceptRequestResponse>builder()
                .items(items)
                .nextCursor(hasNext ? String.valueOf(page + 1) : null)
                .build();
    }

    // Danh sách người dùng chờ duyệt vào nhóm
    @Override
    public PageResponse<GroupPendingRequestResponse> getPendingRequests(String roomId, String userId, String cursor, int limit) {
        int page = (cursor == null || cursor.isEmpty()) ? 0 : Integer.parseInt(cursor);
        Pageable pageable = PageRequest.of(page, limit);

        List<RoomNodeRepository.PendingRequestData> requestDataList = roomNodeRepository.findPendingRequests(roomId, pageable);

        boolean hasNext = requestDataList.size() > limit;
        List<RoomNodeRepository.PendingRequestData> currentRequests = hasNext ? requestDataList.subList(0, limit) : requestDataList;

        if (currentRequests.isEmpty()) {
            return PageResponse.<GroupPendingRequestResponse>builder().items(List.of()).build();
        }

        Set<String> userIdsToFetch = new HashSet<>();
        for (RoomNodeRepository.PendingRequestData req : currentRequests) {
            if (req.targetUserId() != null) userIdsToFetch.add(req.targetUserId());
            if (req.inviterId() != null) userIdsToFetch.add(req.inviterId());
        }

        Map<String, UserResponse> userProfileMap = userService.getUsersMapByIds(new ArrayList<>(userIdsToFetch));

        Room room = roomService.getRoomById(roomId, false);
        String roomName = (room != null) ? room.getRoomName() : "Nhóm ToboChat";

        List<GroupPendingRequestResponse> items = currentRequests.stream().map(req -> GroupPendingRequestResponse.builder()
                .roomId(roomId)
                .roomName(roomName)
                .user(userProfileMap.get(req.targetUserId())) // Lấy profile người chờ duyệt
                .inviter(userProfileMap.get(req.inviterId())) // Lấy profile người gửi lời mời
                .build()).toList();

        return PageResponse.<GroupPendingRequestResponse>builder()
                .items(items)
                .nextCursor(hasNext ? String.valueOf(page + 1) : null)
                .build();
    }

    // Tạo lời mời vào nhóm
    @Override
    public void createGroupAcceptRequestNeo4j(String roomId, String inviterId, String targetUserId) {
        roomNodeRepository.createSentRequest(roomId, inviterId, targetUserId);
    }

    // Tạo lời mời chờ duyệt
    @Override
    public void createGroupPendingRequestNeo4j(String roomId, String inviterId, String targetUserId) {
        roomNodeRepository.createPendingRequest(roomId, inviterId, targetUserId);
    }

    @Override
    public void respondInviteNeo4j(String userId, String roomId, boolean accepted) {
        MemberStatus memberStatus = getMemberStatusNeo4j(roomId, userId);
        if (memberStatus != MemberStatus.SENT) return;

        if (!accepted) {
            deleteMemberRelationshipNeo4j(roomId, userId);
            return;
        }

        addMember(roomId, userId);

        // Tạo tin nhắn hệ thống
        eventPublisher.publishEvent(
                new SystemMessageCreateEvent(
                        roomId,
                        userId,
                        SystemAction.GROUP_INVITE_ACCEPTED,
                        null));
    }

    @Override
    public PageResponse<String> getJoinedRoomIdsNeo4j(String userId, String cursor, int limit) {
        int page = (cursor == null || cursor.isEmpty()) ? 0 : Integer.parseInt(cursor);
        Pageable pageable = PageRequest.of(page, limit);

        List<String> roomIds = roomNodeRepository.findRoomIdsByUserId(userId, pageable);

        boolean hasNext = roomIds.size() > pageable.getPageSize();

        List<String> currentRoomIds = hasNext ? roomIds.subList(0, limit) : roomIds;

        return PageResponse.<String>builder()
                .items(currentRoomIds)
                .nextCursor(hasNext ? String.valueOf(page + 1) : null)
                .build();
    }

    @Override
    public MemberStatus getMemberStatusNeo4j(String roomId, String userId) {
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
        roomNodeRepository.deleteRelationship(roomId, userId);
    }

}
