package com.teamtobo.tobochatserver.entities.documents;

import com.teamtobo.tobochatserver.entities.enums.MessageStatus;
import com.teamtobo.tobochatserver.entities.enums.MessageType;
import com.teamtobo.tobochatserver.entities.enums.SystemAction;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class LatestMessage {
    private String userId;
    private String roomId;
    private String messageId;
    MessageType messageType;
    private String content;
    private Integer fileSize; // số lượng file trong tin nhắn
    private Integer mediaSize; // số lượng ảnh/video
    private String createdAt;
    private MessageStatus messageStatus;
    Map<String, String> metadata;
    SystemAction action;
}
