package com.teamtobo.tobochatserver.entities;

import com.teamtobo.tobochatserver.entities.enums.EntityType;
import com.teamtobo.tobochatserver.entities.enums.ReactionType;
import lombok.*;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;

import java.util.HashSet;
import java.util.Set;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@DynamoDbBean
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageReaction extends BaseEntity {
    // pk: MESSAGE#msgId
    // sk: REACTION#userId
    @Builder.Default
    Set<String> reactions = new HashSet<>();
    @Override
    public EntityType getEntityType() {
        return EntityType.MESSAGE_REACTION;
    }
}
