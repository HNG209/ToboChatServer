package com.teamtobo.tobochatserver.entities.enums;

public enum SystemAction {
    ROOM_CREATED,      // Tạo nhóm
    FRIEND_ACCEPTED,   // Đồng ý kết bạn
    MEMBER_ADDED,      // Thêm thành viên
    MEMBER_ROLE_UPDATED,
    MEMBER_APPROVED, // Đã phê duyệt
    GROUP_INVITE_ACCEPTED, // Chấp nhận lời mời vào nhóm
    MEMBER_LEFT,       // Rời nhóm
    MEMBER_REMOVED,    // Kick thành viên
    ROOM_NAME_CHANGED, // Đổi tên nhóm
    ROOM_AVATAR_CHANGED,     // Đổi ảnh nhóm
    POLL_UPDATED, // Cập nhật poll
    POLL_VOTED, // Tham gia bình chọn
}
