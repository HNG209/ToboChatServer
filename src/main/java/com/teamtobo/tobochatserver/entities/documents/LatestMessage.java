package com.teamtobo.tobochatserver.entities.documents;

import com.teamtobo.tobochatserver.entities.enums.MessageStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@DynamoDbBean
public class LatestMessage {
    private String userId;
    private String messageId;
    private String content;
    private Integer attachmentSize; // số lượng attachment trong tin nhắn
    private String createdAt;
    private MessageStatus messageStatus;
}
