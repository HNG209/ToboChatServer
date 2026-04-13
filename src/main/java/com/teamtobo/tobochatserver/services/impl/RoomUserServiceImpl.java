package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import com.teamtobo.tobochatserver.services.RoomService;
import com.teamtobo.tobochatserver.services.RoomUserService;
import com.teamtobo.tobochatserver.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RoomUserServiceImpl implements RoomUserService {
    private final RoomService roomService;
    private final UserService userService;
    private final RoomMemberService roomMemberService;
    @Override
    public RoomResponse getRoomMetadata(String userId, String roomId) { // lấy tên phòng
        Room room = roomService.getRoomById(roomId, true);

        if (room == null) { // Fallback khi phòng chưa tồn tại
            String[] parts = roomId.split("_");

            if (parts.length != 2) {
                throw new AppException(ErrorCode.ROOM_INVALID);
            }

            // Lấy ID của người kia bằng cách loại trừ ID của chính mình
            String otherUserId = parts[0].equals(userId) ? parts[1] :
                    (parts[1].equals(userId) ? parts[0] : null);

            // Nếu user hiện tại không nằm trong chuỗi ID phòng
            if (otherUserId == null) {
                throw new AppException(ErrorCode.ROOM_INVALID);
            }

            UserResponse stranger = userService.getUserProfile(otherUserId);

            return RoomResponse.builder()
                    .id(roomId)
                    .roomName(stranger.getName())
                    .avatarUrl(stranger.getAvatarUrl())
                    .roomType(RoomType.DM)
                    .build();
        }

        int unreadCount = roomMemberService.getUnreadCount(userId, roomId);
        if (room.getRoomType() == RoomType.DM) {
            List<String> memberIds = roomService.getMembersByRoomId(roomId);
            if (memberIds.size() <= 2) {
                memberIds.stream()
                        .filter(id -> !id.equals(userId))
                        .findFirst().ifPresent(otherUserId -> {
                            UserResponse other = userService.getUserProfile(otherUserId);
                            room.setRoomName(other.getName());
                            room.setAvatarUrl(other.getAvatarUrl());
                        });

            }
        } else { // GROUP
//            List<String> memberIds = roomService.getMembersByRoomId(Helper.normalizeId(i.getPk()));
//            if (memberIds.size() > 2) {
//                String groupName = memberIds.stream()
//                        .limit(3)
//                        .map(memberId -> userService.getUserProfile(memberId).getName())
//                        .collect(Collectors.joining(", "));
//                responseBuilder.roomName(groupName);
//            } else {
//                responseBuilder.roomName(i.getRoomName());
//            }
        }

        return RoomResponse.builder()
                .id(roomId)
                .roomName(room.getRoomName())
                .avatarUrl(room.getAvatarUrl())
                .roomType(room.getRoomType())
                .unreadMessages(unreadCount)
                .build();
    }
}
