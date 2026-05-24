package com.teamtobo.tobochatserver.configs;

import com.teamtobo.tobochatserver.entities.*;
import com.teamtobo.tobochatserver.entities.documents.AttachmentItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDbConfig {
    @Value("${aws.dynamodb.tableName:ToboChatTable}")
    private String tableName;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        // Tự động lấy credential trong máy (.aws/credentials) hoặc biến môi trường
        return DynamoDbClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public DynamoDbTable<User> userTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(User.class));
    }

    @Bean
    public DynamoDbTable<Friend> friendTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(Friend.class));
    }

    @Bean
    public DynamoDbTable<FriendRequest> friendRequestTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(FriendRequest.class));
    }

    @Bean
    public DynamoDbTable<Room> RoomTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(Room.class));
    }

    @Bean
    public DynamoDbTable<RoomMember> RoomMemberTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(RoomMember.class));
    }

    @Bean
    public DynamoDbTable<Message> MessageTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(Message.class));
    }

    @Bean
    public DynamoDbTable<MessageReaction> MessageReactionTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(MessageReaction.class));
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    //Hào
    @Bean
    public DynamoDbTable<GroupAcceptRequest> groupAcceptRequestTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(GroupAcceptRequest.class));
    }

    @Bean
    public DynamoDbTable<GroupPendingRequest> groupPendingRequestTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(GroupPendingRequest.class));
    }

    @Bean
    public DynamoDbTable<AttachmentItem> attachmentItemTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table(tableName, TableSchema.fromBean(AttachmentItem.class));
    }
}
