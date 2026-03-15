package com.teamtobo.tobochatserver.entities;

import com.teamtobo.tobochatserver.entities.enums.EntityType;
import lombok.AccessLevel;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.experimental.SuperBuilder;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@EqualsAndHashCode(callSuper = true)
@Data
@SuperBuilder
@NoArgsConstructor
@DynamoDbBean
@FieldDefaults(level = AccessLevel.PRIVATE)
public class User extends BaseEntity { // Không lưu mật khẩu, để Cognito lưu
    String name;
    String email;
    String avatarUrl;

    String searchPk;
    String searchSk;
    @Override
    public EntityType getEntityType() {
        return EntityType.USER;
    }

    @Override
    @DynamoDbPartitionKey
    public String getPk() {
        return super.getPk();
    }

    @Override
    @DynamoDbSortKey
    public String getSk() {
        return "PROFILE";
    }

    @DynamoDbSecondaryPartitionKey(indexNames = "GSI_EmailSearch")
    public String getSearchPk() {
        if (this.searchPk != null) return this.searchPk;

        if (email == null || email.isEmpty()) return "ENTITY#USER#OTHER";
        char firstChar = email.trim().toUpperCase().charAt(0);
        return Character.isLetter(firstChar) ? "ENTITY#USER#" + firstChar : "ENTITY#USER#OTHER";
    }

    @DynamoDbSecondarySortKey(indexNames = "GSI_EmailSearch")
    public String getSearchSk() {
        return email != null ? email.toLowerCase() : null;
    }
}
