package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.request.CreateDmRoomRequest;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.User;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {

    private final DynamoDbTable<User> userTable;
    private final DynamoDbTable<RoomMember> roomMemberTable;
    private final DynamoDbEnhancedClient enhancedClient;

    /**
     * Tạo roomId duy nhất và cố định cho một cặp DM bằng cách sắp xếp
     * hai userId theo thứ tự từ điển, đảm bảo idempotency.
     */
    private String buildDmRoomId(String userId1, String userId2) {
        if (userId1.compareTo(userId2) <= 0) {
            return userId1 + "_" + userId2;
        }
        return userId2 + "_" + userId1;
    }

    private User getUserById(String userId) {
        User user = userTable.getItem(Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("PROFILE")
                .build());
        if (user == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    @Override
    public RoomResponse getOrCreateDmRoom(String userId, CreateDmRoomRequest request) {
        String peerId = request.getPeerId();

        if (userId.equals(peerId)) {
            throw new AppException(ErrorCode.CANNOT_ADD_SELF);
        }

        String roomId = buildDmRoomId(userId, peerId);

        // Kiểm tra xem phòng DM đã tồn tại chưa
        RoomMember existing = roomMemberTable.getItem(Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("ROOM#" + roomId)
                .build());

        if (existing != null) {
            return toRoomResponse(existing);
        }

        // Lấy thông tin của cả hai người dùng để lưu vào RoomMember
        User currentUser = getUserById(userId);
        User peerUser = getUserById(peerId);

        // Tạo RoomMember cho người dùng hiện tại (lưu thông tin của peer)
        RoomMember memberForCurrentUser = RoomMember.builder()
                .pk("USER#" + userId)
                .sk("ROOM#" + roomId)
                .roomType(RoomType.DM.name())
                .peerId("USER#" + peerId)
                .peerName(peerUser.getName())
                .peerAvatarUrl(peerUser.getAvatarUrl())
                .build();

        // Tạo RoomMember cho peer (lưu thông tin của người dùng hiện tại)
        RoomMember memberForPeer = RoomMember.builder()
                .pk("USER#" + peerId)
                .sk("ROOM#" + roomId)
                .roomType(RoomType.DM.name())
                .peerId("USER#" + userId)
                .peerName(currentUser.getName())
                .peerAvatarUrl(currentUser.getAvatarUrl())
                .build();

        // Ghi cả hai bản ghi trong một transaction
        enhancedClient.transactWriteItems(b -> b
                .addPutItem(roomMemberTable, memberForCurrentUser)
                .addPutItem(roomMemberTable, memberForPeer)
        );

        log.info("✅ Đã tạo phòng DM [{}] giữa [{}] và [{}]", roomId, userId, peerId);

        return toRoomResponse(memberForCurrentUser);
    }

    @Override
    public List<RoomResponse> getRooms(String userId) {
        String pk = "USER#" + userId;

        // Truy vấn tất cả bản ghi có PK=USER#{userId} và SK bắt đầu bằng ROOM#
        List<RoomMember> memberships = roomMemberTable
                .query(QueryEnhancedRequest.builder()
                        .queryConditional(
                                QueryConditional.sortBeginsWith(
                                        Key.builder()
                                                .partitionValue(pk)
                                                .sortValue("ROOM#")
                                                .build()
                                )
                        )
                        .build())
                .items()
                .stream()
                .toList();

        return memberships.stream()
                .map(this::toRoomResponse)
                .toList();
    }

    /**
     * Chuyển đổi RoomMember thành RoomResponse.
     * Đối với phòng DM, thông tin bạn bè được lấy trực tiếp từ bản ghi RoomMember
     * (đã lưu sẵn khi tạo phòng) — không cần truy vấn thêm.
     */
    private RoomResponse toRoomResponse(RoomMember member) {
        // Lấy roomId từ SK (ROOM#{roomId} -> roomId)
        String roomId = member.getSk().substring("ROOM#".length());

        RoomResponse.RoomResponseBuilder builder = RoomResponse.builder()
                .id(roomId)
                .type(member.getRoomType());

        if (RoomType.DM.name().equals(member.getRoomType())) {
            builder.peer(RoomResponse.PeerInfo.builder()
                    .id(member.getPeerId())
                    .name(member.getPeerName())
                    .avatarUrl(member.getPeerAvatarUrl())
                    .build());
        } else {
            builder.name(member.getRoomName())
                    .avatarUrl(member.getRoomAvatarUrl());
        }

        return builder.build();
    }
}
