package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.PresignedUploadResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.enums.RoomType;

import java.util.List;

public interface RoomService {
//    void createRoom(String userId, RoomCreateRequest request, RoomType roomType);
    List<String> getMembersByRoomId(String roomId);
    Room getRoomById(String roomId, boolean skipException);
    PresignedUploadResponse getRoomAvatarUploadUrl(String roomId, String contentType);
}
