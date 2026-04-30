package com.teamtobo.tobochatserver.entities.enums;

public enum MemberStatus {
    NOT_IN_GROUP, // Chưa vào nhóm
    ADDED, // Đã add vào nhóm ngay lập tức
    SENT, // Đã gửi lời mời nếu người nhận tắt tự động thêm vào nhóm
    PENDING // Đang chờ duyệt nếu người thêm khác admin/vice
}
