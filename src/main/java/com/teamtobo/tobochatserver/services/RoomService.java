package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;

public interface RoomService {

    /**
     * Tạo mới hoặc lấy Room DM đã tồn tại giữa userId và friendId.
     *
     * @param userId   ID của người dùng hiện tại
     * @param friendId ID của người bạn
     * @return thông tin Room (bao gồm thông tin của người bạn)
     */
    RoomResponse createOrGetDmRoom(String userId, String friendId);

    /**
     * Lấy danh sách Rooms của người dùng.
     * Với DM rooms, name và avatarUrl là thông tin của người bạn.
     *
     * @param userId ID của người dùng hiện tại
     * @param cursor con trỏ phân trang
     * @param limit  số lượng tối đa
     * @return danh sách RoomResponse có phân trang
     */
    PageResponse<RoomResponse> getUserRooms(String userId, String cursor, int limit);
}
