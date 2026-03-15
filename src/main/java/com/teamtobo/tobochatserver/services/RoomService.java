package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.CreateDmRoomRequest;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;

import java.util.List;

public interface RoomService {

    /**
     * Tạo mới hoặc trả về phòng DM hiện có giữa hai người dùng.
     * Nếu phòng DM đã tồn tại thì trả về phòng đó thay vì tạo mới.
     *
     * @param userId  ID người dùng hiện tại (lấy từ JWT)
     * @param request chứa peerId - ID của người dùng cần mở DM cùng
     * @return thông tin phòng DM kèm thông tin bạn bè
     */
    RoomResponse getOrCreateDmRoom(String userId, CreateDmRoomRequest request);

    /**
     * Lấy danh sách tất cả các phòng mà người dùng tham gia.
     * Đối với phòng DM, kết quả trả về kèm thông tin bạn bè (peer).
     *
     * @param userId ID người dùng hiện tại
     * @return danh sách phòng
     */
    List<RoomResponse> getRooms(String userId);
}
