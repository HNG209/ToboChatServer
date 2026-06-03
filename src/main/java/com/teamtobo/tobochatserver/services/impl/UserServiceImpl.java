package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.teamtobo.tobochatserver.dtos.events.UnreadFriendRequestUpdateEvent;
import com.teamtobo.tobochatserver.dtos.request.FriendAcceptRequest;
import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.dtos.request.UserUpdateRequest;
import com.teamtobo.tobochatserver.dtos.response.*;
import com.teamtobo.tobochatserver.entities.*;
import com.teamtobo.tobochatserver.entities.enums.*;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.*;
import com.teamtobo.tobochatserver.utils.CognitoHelper;
import com.teamtobo.tobochatserver.utils.Helper;
import com.teamtobo.tobochatserver.utils.S3Helper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.time.Instant;
import java.util.*;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final DynamoDbTable<User> userTable;
    private final CognitoIdentityProviderClient cognitoClient;
    private final RoomService roomService;

    private final S3Helper s3Helper;
    private final CognitoHelper cognitoHelper;
    private final Map<String, String> mfaCache = new ConcurrentHashMap<>();
    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient enhancedClient;
    private final SocketIOServer socketIOServer;

    @Value("${aws.cognito.userPoolId}")
    private String userPoolId;

    @Value("${aws.cognito.appClientId}")
    private String appClientId;

    @Override
    public User getUserById(String userId) {
        // Query DynamoDB bằng PK (USER#id) và SK (PROFILE)
        User user = userTable.getItem(Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("PROFILE")
                .build());

        if (user == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    @Override
    public Map<String, UserResponse> getUsersMapByIds(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return new HashMap<>();
        }

        // Loại bỏ các ID trùng lặp
        List<String> uniqueIds = userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, UserResponse> userProfileMap = new HashMap<>();

        int batchSize = 100;
        for (int i = 0; i < uniqueIds.size(); i += batchSize) {
            List<String> chunk = uniqueIds.subList(i, Math.min(uniqueIds.size(), i + batchSize));

            ReadBatch.Builder<User> readBatchBuilder = ReadBatch.builder(User.class)
                    .mappedTableResource(userTable);

            chunk.forEach(id -> readBatchBuilder.addGetItem(Key.builder()
                    .partitionValue("USER#" + id)
                    .sortValue("PROFILE")
                    .build()));

            BatchGetResultPageIterable batchResults = enhancedClient.batchGetItem(r -> r.addReadBatch(readBatchBuilder.build()));

            // Đọc kết quả của lô hiện tại và map vào kết quả
            batchResults.resultsForTable(userTable).forEach(user -> {
                UserResponse responseDto = UserResponse.builder()
                        .id(user.getUserId())
                        .name(user.getName())
                        .avatarUrl(user.getAvatarUrl())
                        .email(user.getEmail())
                        .allowAutoAddToGroup(user.isAllowAutoAddToGroup())
                        .build();
                userProfileMap.put(user.getUserId(), responseDto);
            });
        }

        return userProfileMap;
    }

    @Override
    public UserResponse getUserProfile(String userId) {
        User user = getUserById(userId);
        return UserResponse.builder()
                .id(user.getPk())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .dob(user.getDob())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .friendRequestCount(user.getFriendRequestCount())
                .groupRequestCount(user.getGroupRequestCount())
                .totalUnreadMessages(user.getTotalUnreadMessages())
                .allowAutoAddToGroup(user.isAllowAutoAddToGroup())
                .build();
    }

    @Override
    public User updateUserProfile(String userId, UserUpdateRequest request) {
        User user = getUserById(userId);
        boolean isChanged = false;

        // 1. Update name
        if (request.getName() != null
                && !request.getName().isBlank()
                && !request.getName().equals(user.getName())) {

            user.setName(request.getName());
            cognitoHelper.syncNameToCognito(userId, request.getName());
            isChanged = true;
        }

        // 2. Update DOB
        if (request.getDob() != null
                && !request.getDob().isBlank()
                && !request.getDob().equals(user.getDob())) {

            user.setDob(request.getDob());
            isChanged = true;
        }

        if(request.getAllowAutoAddToGroup() != null) {
            user.setAllowAutoAddToGroup(request.getAllowAutoAddToGroup());
            isChanged = true;
        }

        // 3. Lưu ngược lại DynamoDB
        if (isChanged) {
            userTable.updateItem(user);
        }

        return user;
    }

    public MfaInitResponse initEnableMFA(String userId, String password) {
        // 1. Verify password + lấy accessToken
        Map<String, String> authParams = new HashMap<>();
        authParams.put("USERNAME", userId);
        authParams.put("PASSWORD", password);

        AdminInitiateAuthResponse authResponse =
                cognitoClient.adminInitiateAuth(
                        AdminInitiateAuthRequest.builder()
                                .userPoolId(userPoolId)
                                .clientId(appClientId)
                                .authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                                .authParameters(authParams)
                                .build()
                );

        String accessToken = authResponse.authenticationResult().accessToken();

        // 2. Associate software token (TOTP)
        AssociateSoftwareTokenResponse associateSoftwareTokenResponse =
                cognitoClient.associateSoftwareToken(
                        AssociateSoftwareTokenRequest.builder()
                                .accessToken(accessToken)
                                .build()
                );

        String secretCode = associateSoftwareTokenResponse.secretCode();
        mfaCache.put(userId, accessToken);
        return new MfaInitResponse(secretCode);
    }

    @Override
    public void confirmEnableMFA(String userId, String otp) {
        // 1. Lấy lại accessToken đã lưu tạm
        String accessToken = mfaCache.get(userId);

        if (accessToken == null) {
            throw new RuntimeException("MFA session expired");
        }

        // 2. Verify OTP
        cognitoClient.verifySoftwareToken(
                VerifySoftwareTokenRequest.builder()
                        .accessToken(accessToken)
                        .userCode(otp)
                        .build()
        );

        // 3. Set MFA preference
        cognitoClient.adminSetUserMFAPreference(
                AdminSetUserMfaPreferenceRequest.builder()
                        .userPoolId(userPoolId)
                        .username(userId)
                        .softwareTokenMfaSettings(
                                SoftwareTokenMfaSettingsType.builder()
                                        .enabled(true)
                                        .preferredMfa(true)
                                        .build()
                        )
                        .build()
        );

        // 4. Xóa session tạm
        mfaCache.remove(userId);
    }

    @Override
    public void disableMFA(String userId, String password) {
        // 1. Verify password
        Map<String, String> authParams = new HashMap<>();
        authParams.put("USERNAME", userId);
        authParams.put("PASSWORD", password);

        cognitoClient.adminInitiateAuth(
                AdminInitiateAuthRequest.builder()
                        .userPoolId(userPoolId)
                        .clientId(appClientId)
                        .authFlow(AuthFlowType.ADMIN_NO_SRP_AUTH)
                        .authParameters(authParams)
                        .build()
        );

        // 2. Disable MFA
        cognitoClient.adminSetUserMFAPreference(
                AdminSetUserMfaPreferenceRequest.builder()
                        .userPoolId(userPoolId)
                        .username(userId)
                        .softwareTokenMfaSettings(
                                SoftwareTokenMfaSettingsType.builder()
                                        .enabled(false)
                                        .preferredMfa(false)
                                        .build()
                        )
                        .build()
        );
    }

    @Override
    public PresignedUploadResponse getAvatarUploadUrl(String userId, String contentType) {
        // Có thể thêm validate khác nếu cần (ví dụ: user tồn tại)
        return s3Helper.generatePresignedUploadUrl(userId, contentType);
    }

    @Override
    public void increaseFriendRequestCount(String userId, String senderId) {
        this.updateFriendRequestCount(userId, 1);
        UserResponse userResponse = getUserProfile(senderId);
        FriendRequestResponse friendResponse = FriendRequestResponse.builder()
                .id(senderId)
                .name(userResponse.getName())
                .avatarUrl(userResponse.getAvatarUrl())
                .createdAt(userResponse.getCreatedAt())
                .build();
        socketIOServer.getRoomOperations(userId).sendEvent("friend_request_unread_update", friendResponse);
        log.info("send unread friend request");
    }

    @Override
    public void markReadFriendRequest(String userId) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName("ToboChatTable")
                .key(Map.of(
                        "pk", AttributeValue.builder().s("USER#" + userId).build(),
                        "sk", AttributeValue.builder().s("PROFILE").build()
                ))
                .updateExpression("SET friendRequestCount = :zero")
                .expressionAttributeValues(Map.of(
                        ":zero", AttributeValue.builder().n("0").build()
                ))
                .build());

        socketIOServer.getRoomOperations(userId).sendEvent("friend_request_unread_reset", 1);
    }

    private void updateFriendRequestCount(String userId, int amount) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                        .tableName("ToboChatTable")
                        .key(Map.of(
                                "pk", AttributeValue.builder().s("USER#" + userId).build(),
                                "sk", AttributeValue.builder().s("PROFILE").build()
                        ))
                        .updateExpression("SET friendRequestCount = if_not_exists(friendRequestCount, :zero) + :inc")
                        .expressionAttributeValues(Map.of(
                                ":inc", AttributeValue.builder().n(String.valueOf(amount)).build(),
                                ":zero", AttributeValue.builder().n("0").build()
                        ))
                .build());
    }

    @Override
    public void increaseGroupRequestCount(String userId, String senderId, String roomId) {
        this.updateGroupRequestCount(userId, 1);
        UserResponse userResponse = getUserProfile(senderId);
        UserResponse cleanUserResponse = UserResponse.builder()
                .id(userResponse.getId())
                .name(userResponse.getName())
                .email(userResponse.getEmail())
                .avatarUrl(userResponse.getAvatarUrl())
                .createdAt(userResponse.getCreatedAt())
                .build();

        Room room = roomService.getRoomById(roomId, false);
        GroupAcceptRequestResponse groupAcceptRequestResponse = GroupAcceptRequestResponse.builder()
                .inviter(cleanUserResponse)
                .roomId(room.getRoomId())
                .roomName(room.getRoomName())
                .build();
        socketIOServer.getRoomOperations(userId).sendEvent("group_request_unread_update", groupAcceptRequestResponse);
        log.info("send unread group request");
    }

    @Override
    public void markReadGroupRequest(String userId) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                        .tableName("ToboChatTable")
                        .key(Map.of(
                                "pk", AttributeValue.builder().s("USER#" + userId).build(),
                                "sk", AttributeValue.builder().s("PROFILE").build()
                        ))
                        .updateExpression("SET groupRequestCount = :zero")
                        .expressionAttributeValues(Map.of(
                                ":zero", AttributeValue.builder().n("0").build()
                        ))
                .build());
        socketIOServer.getRoomOperations(userId).sendEvent("group_request_unread_reset", 1);
    }

    private void updateGroupRequestCount(String userId, int amount) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                        .tableName("ToboChatTable")
                        .key(Map.of(
                                "pk", AttributeValue.builder().s("USER#" + userId).build(),
                                "sk", AttributeValue.builder().s("PROFILE").build()
                        ))
                        .updateExpression("SET groupRequestCount = if_not_exists(groupRequestCount, :zero) + :inc")
                        .expressionAttributeValues(Map.of(
                                ":inc", AttributeValue.builder().n(String.valueOf(amount)).build(),
                                ":zero", AttributeValue.builder().n("0").build()
                        ))
                .build());
    }

}
