package com.teamtobo.tobochatserver.entities;

import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@DynamoDbBean
@FieldDefaults(level = AccessLevel.PRIVATE)
public class Room extends BaseEntity {
    String roomId;               // UUID chung cho cả 2 người (với DM) hoặc room group
    String type;                 // "DM" hoặc "GROUP"

    // Dành cho DM: lưu thông tin của người kia (để FE hiển thị không cần query thêm)
    String participantId;        // ID của người bạn đang chat cùng
    String participantName;      // Tên hiển thị của người đó
    String participantAvatarUrl; // Avatar của người đó

    // Dành cho GROUP
    String name;
    String avatarUrl;

    // Tin nhắn gần nhất
    String lastMessage;
    String lastMessageAt;

    @Override
    public String getEntityType() {
        return "ROOM";
    }
}
