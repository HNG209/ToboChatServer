package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
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
    @Override
    public RoomResponse getRoomMetadata(String userId, String roomId) {
        Room room = roomService.getRoomById(roomId);

        if (room.getRoomType() == RoomType.DM) {
            List<String> memberIds = roomService.getMembersByRoomId(roomId);
            if (memberIds.size() <= 2) {
                memberIds.stream()
                        .filter(id -> !id.equals(userId))
                        .findFirst().ifPresent(otherUserId -> room.setRoomName(userService.getUserProfile(otherUserId).getName()));

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
                .roomType(room.getRoomType())
                .build();
    }
}
