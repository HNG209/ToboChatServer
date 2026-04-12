package com.teamtobo.tobochatserver.configs;

import com.teamtobo.tobochatserver.entities.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

@Configuration
public class DynamoDbConfig {
    @Bean
    public DynamoDbClient dynamoDbClient() {
        // Tự động lấy credential trong máy (.aws/credentials) hoặc biến môi trường
        return DynamoDbClient.builder()
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    @Bean
    public DynamoDbTable<User> userTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("ToboChatTable", TableSchema.fromBean(User.class));
    }

    @Bean
    public DynamoDbTable<Friend> friendTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("ToboChatTable", TableSchema.fromBean(Friend.class));
    }

    @Bean
    public DynamoDbTable<FriendRequest> friendRequestTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("ToboChatTable", TableSchema.fromBean(FriendRequest.class));
    }

    @Bean
    public DynamoDbTable<Room> RoomTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("ToboChatTable", TableSchema.fromBean(Room.class));
    }

    @Bean
    public DynamoDbTable<RoomMember> RoomMemberTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("ToboChatTable", TableSchema.fromBean(RoomMember.class));
    }

    @Bean
    public DynamoDbTable<Message> MessageTable(DynamoDbEnhancedClient enhancedClient) {
        return enhancedClient.table("ToboChatTable", TableSchema.fromBean(Message.class));
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}
