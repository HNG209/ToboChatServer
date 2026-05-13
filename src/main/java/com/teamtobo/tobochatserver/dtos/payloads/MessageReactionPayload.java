package com.teamtobo.tobochatserver.dtos.payloads;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.teamtobo.tobochatserver.entities.enums.ReactionType;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageReactionPayload {
    String roomId;
    String messageId;
    String userId;
    ReactionType reactionType;
}
