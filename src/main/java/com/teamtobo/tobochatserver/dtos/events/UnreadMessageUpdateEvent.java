package com.teamtobo.tobochatserver.dtos.events;

import com.teamtobo.tobochatserver.entities.enums.UnreadUpdateType;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UnreadMessageUpdateEvent {
    private String userId; // update tổng thông báo của người dùng
    private String roomId; // update thông báo của phòng(tin nhắn chưa đọc)
    UnreadUpdateType type; // cập nhật hoặc reset
}
