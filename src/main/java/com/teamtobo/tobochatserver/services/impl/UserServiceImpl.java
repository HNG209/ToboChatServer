package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.request.FriendAcceptRequest;
import com.teamtobo.tobochatserver.dtos.request.UserUpdateRequest;
import com.teamtobo.tobochatserver.dtos.response.*;
import com.teamtobo.tobochatserver.entities.Friend;
import com.teamtobo.tobochatserver.entities.FriendRequest;
import com.teamtobo.tobochatserver.entities.User;
import com.teamtobo.tobochatserver.entities.enums.FriendRequestType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final DynamoDbTable<User> userTable;
    private final DynamoDbTable<Friend> friendTable;
    private final DynamoDbTable<FriendRequest> friendRequestTable;
    private final DynamoDbEnhancedClient enhancedClient;
    private final CognitoIdentityProviderClient cognitoClient;
    private final S3Client s3Client;
    private final Map<String, String> mfaCache = new ConcurrentHashMap<>();

    @Value("${aws.cognito.userPoolId}")
    private String userPoolId;

    @Value("${aws.cognito.appClientId}")
    private String appClientId;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    private User getUserById(String userId) {
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
    public UserResponse getUserProfile(String userId) {
        User user = getUserById(userId);
        return UserResponse.builder()
                .id(user.getPk())
                .name(user.getName())
                .avatarUrl(user.getAvatarUrl())
                .email(user.getEmail())
                .createdAt(user.getCreatedAt())
                .build();
    }

    @Override
    public User updateUserProfile(String userId, UserUpdateRequest request) {
        User user = getUserById(userId);
        boolean isChanged = false;

        // 2. Xử lý Upload Avatar (Nếu có gửi file)
        if (request.getAvatar() != null && !request.getAvatar().isEmpty()) {
            String s3Url = uploadFileToS3(userId, request.getAvatar());
            user.setAvatarUrl(s3Url); // Cập nhật URL mới vào entity
            isChanged = true;
        }

        // 3. Xử lý đổi tên (Nếu có gửi tên mới)
        if (request.getName() != null && !request.getName().isBlank() && !request.getName().equals(user.getName())) {
            user.setName(request.getName());
            syncNameToCognito(userId, request.getName()); // Đồng bộ sang Cognito
            isChanged = true;
        }

        // 4. Lưu ngược lại DynamoDB
        if (isChanged) {
            userTable.updateItem(user);
        }

        return user;
    }

    @Override
    public void sendFriendRequest(String userId, String otherId) {
        // 0. Kiểm tra không thể gửi lời mời kết bạn cho chính mình
        if (userId.equals(otherId)) {
            throw new AppException(ErrorCode.CANNOT_ADD_SELF);
        }

        // 1. Kiểm tra người được gửi lời mời có tồn tại để lấy fullName của họ
        User other = getUserById(otherId);

        // 2. Kiểm tra đã là bạn chưa
        Friend existingFriend = friendTable.getItem(Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("FRIEND#" + otherId)
                .build());
        if (existingFriend != null) {
            throw new AppException(ErrorCode.ALREADY_FRIENDS);
        }

        // 3. Kiểm tra Friend Request đã gửi chưa (userId -> otherId)
        FriendRequest existingRequest = friendRequestTable.getItem(Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("REQUEST#" + otherId)
                .build());
        if (existingRequest != null) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
        }

        // 4. Kiểm tra Friend Request đã nhận chưa (otherId -> userId)
        FriendRequest incomingRequest = friendRequestTable.getItem(Key.builder()
                .partitionValue("USER#" + otherId)
                .sortValue("REQUEST#" + userId)
                .build());
        if (incomingRequest != null) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
        }

        // 5. Tạo friend request
        FriendRequest friendRequest = FriendRequest.builder()
                .pk("USER#" + userId)
                .sk("REQUEST#" + otherId)
                .gsi1pk("REQUEST#" + otherId)
                .gsi1sk("USER#" + userId)
                .avatarUrl(other.getAvatarUrl())
                .name(other.getName())
                .build();

        friendRequestTable.putItem(friendRequest);
    }

    @Override
    public void cancelFriendRequest(String userId, String otherId) {
        Key requestKey = Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("REQUEST#" + otherId)
                .build();

        FriendRequest existingRequest = friendRequestTable.getItem(requestKey);
        if (existingRequest == null) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }

        friendRequestTable.deleteItem(requestKey);
    }

    @Override
    public void responseFriendRequest(String userId, FriendAcceptRequest request) {
        // Định nghĩa các khóa để xác định Request cũ
        Key requestKey = Key.builder()
                .partitionValue("USER#" + request.getFromUser())
                .sortValue("REQUEST#" + userId)
                .build();

        // Kiểm tra request có tồn tại không
        FriendRequest existingRequest = friendRequestTable.getItem(requestKey);
        if (existingRequest == null) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }

        if (!request.isAccepted()) {
            // Xóa yêu cầu nếu từ chối
            friendRequestTable.deleteItem(requestKey);
            return;
        }

        User currentUser = getUserById(userId);
        User senderUser = getUserById(request.getFromUser());

        String now = Instant.now().toString();

        enhancedClient.transactWriteItems(b -> b
                // Xóa bản ghi Request
                .addDeleteItem(friendTable, requestKey)

                // Thêm bạn cho User hiện tại (userId)
                .addPutItem(friendTable, Friend.builder()
                        .pk("USER#" + userId)
                        .sk("FRIEND#" + request.getFromUser())
                        .name(senderUser.getName())
                        .avatarUrl(senderUser.getAvatarUrl())
                        .addedAt(now)
                        .build())

                // Thêm bạn cho người gửi (fromUser)
                .addPutItem(friendTable, Friend.builder()
                        .pk("USER#" + request.getFromUser())
                        .sk("FRIEND#" + userId)
                        .name(currentUser.getName())
                        .avatarUrl(currentUser.getAvatarUrl())
                        .addedAt(now)
                        .build())
        );
    }

    @Override
    public PageResponse<UserResponse> findByEmail(String email, String cursor, int limit) {
        if (email == null || email.isEmpty()) {
            return PageResponse.<UserResponse>builder().items(List.of()).build();
        }

        // 1. Chuẩn hóa chuỗi tìm kiếm và xác định Shard (Partition Key của GSI)
        String searchPrefix = email.trim().toLowerCase();
        char firstChar = searchPrefix.toUpperCase().charAt(0);
        String shardPk = Character.isLetter(firstChar)
                ? "ENTITY#USER#" + firstChar
                : "ENTITY#USER#OTHER";

        // 2. Trỏ tới Index GSI_EmailSearch
        DynamoDbIndex<User> index = userTable.index("GSI_EmailSearch");

        // 3. Xây dựng Query Builder với điều kiện sortBeginsWith
        QueryEnhancedRequest.Builder builder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.sortBeginsWith(
                        k -> k.partitionValue(shardPk).sortValue(searchPrefix)
                ))
                .limit(limit);

        // 4. Xử lý phân trang (Pagination)
        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = new HashMap<>();
            exclusiveStartKey.put("searchPk", AttributeValue.builder().s(shardPk).build());
            exclusiveStartKey.put("searchSk", AttributeValue.builder().s(cursor).build());
            exclusiveStartKey.put("pk", AttributeValue.builder().s("USER#ID_PLACEHOLDER").build());
            exclusiveStartKey.put("sk", AttributeValue.builder().s("PROFILE").build());

            builder.exclusiveStartKey(exclusiveStartKey);
        }

        // 5. Truy vấn
        SdkIterable<Page<User>> results = index.query(builder.build());
        Iterator<Page<User>> iterator = results.iterator();

        if (!iterator.hasNext()) {
            return PageResponse.<UserResponse>builder().items(List.of()).build();
        }

        Page<User> page = iterator.next();

        // 6. Lấy cursor cho trang tiếp theo (email của user cuối cùng trong trang)
        String nextCursor = null;
        if (page.lastEvaluatedKey() != null && page.lastEvaluatedKey().containsKey("searchSk")) {
            nextCursor = page.lastEvaluatedKey().get("searchSk").s();
        }

        return PageResponse.<UserResponse>builder()
                .items(page.items().stream().map(
                        item -> UserResponse.builder()
                                .id(item.getPk())
                                .email(item.getEmail())
                                .avatarUrl(item.getAvatarUrl())
                                .name(item.getName())
                                .build()
                ).toList())
                .nextCursor(nextCursor)
                .build();
    }

    private String uploadFileToS3(String userId, MultipartFile file) {
        // Đặt tên file: users/{userId}/{uuid}-{filename} để tránh trùng
        String fileName = "users/" + userId + "/" + UUID.randomUUID() + "-" + file.getOriginalFilename();

        try {
            PutObjectRequest putOb = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .contentType(file.getContentType())
                    .build();

            // Upload lên S3
            s3Client.putObject(putOb, RequestBody.fromBytes(file.getBytes()));

            // Lấy URL trả về
            return s3Client.utilities().getUrl(GetUrlRequest.builder()
                    .bucket(bucketName)
                    .key(fileName)
                    .build()).toExternalForm();

        } catch (IOException e) {
            throw new AppException(ErrorCode.UPLOAD_ERROR);
        }
    }

    private void syncNameToCognito(String userId, String newName) {
        AttributeType nameAttr = AttributeType.builder().name("name").value(newName).build();
        AdminUpdateUserAttributesRequest request = AdminUpdateUserAttributesRequest.builder()
                .userPoolId(userPoolId)
                .username(userId)
                .userAttributes(nameAttr)
                .build();
        cognitoClient.adminUpdateUserAttributes(request);
    }

    @Override
    public PageResponse<FriendResponse> getFriends(
            String userId,
            String cursor,
            int limit
    ) {

        String pk = "USER#" + userId;

        QueryEnhancedRequest.Builder requestBuilder =
                QueryEnhancedRequest.builder()
                        .queryConditional(
                                QueryConditional.sortBeginsWith(
                                        Key.builder()
                                                .partitionValue(pk)
                                                .sortValue("FRIEND#")
                                                .build()
                                )
                        )
                        .limit(limit);

        // Nếu có cursor thì set ExclusiveStartKey
        if (cursor != null) {
            Map<String, AttributeValue> exclusiveStartKey =
                    Map.of(
                            "pk", AttributeValue.builder().s(pk).build(),
                            "sk", AttributeValue.builder().s(cursor).build()
                    );

            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        Page<Friend> page = friendTable
                .query(requestBuilder.build())
                .stream()
                .findFirst()
                .orElse(null);

        if (page == null) {
            return PageResponse.<FriendResponse>builder()
                    .items(List.of())
                    .nextCursor(null)
                    .build();
        }

        String nextCursor = null;
        if (page.lastEvaluatedKey() != null &&
                page.lastEvaluatedKey().get("sk") != null) {

            nextCursor = page.lastEvaluatedKey().get("sk").s();
        }

        return PageResponse.<FriendResponse>builder()
                .items(page.items().stream().map(
                        i -> FriendResponse.builder()
                                .id(i.getPk())
                                .name(i.getName())
                                .avatarUrl(i.getAvatarUrl())
                                .createdAt(i.getCreatedAt())
                                .build()
                ).toList())
                .nextCursor(nextCursor)
                .build();
    }

    @Override
    public PageResponse<FriendRequestResponse> getFriendRequests(FriendRequestType type,
                                                                 String userId,
                                                                 String cursor,
                                                                 int limit) {
        return switch (type) {
            case SENT -> getSentRequests(userId, cursor, limit);
            case PENDING -> getPendingRequests(userId, cursor, limit);
        };
    }

    private PageResponse<FriendRequestResponse> getSentRequests(
            String userId,
            String cursor,
            int limit
    ) {

        String pk = "USER#" + userId;

        QueryEnhancedRequest.Builder requestBuilder =
                QueryEnhancedRequest.builder()
                        .queryConditional(
                                QueryConditional.sortBeginsWith(
                                        Key.builder()
                                                .partitionValue(pk)
                                                .sortValue("REQUEST#")
                                                .build()
                                )
                        )
                        .limit(limit);

        if (cursor != null) {
            Map<String, AttributeValue> exclusiveStartKey =
                    Map.of(
                            "pk", AttributeValue.builder().s(pk).build(),
                            "sk", AttributeValue.builder().s(cursor).build()
                    );

            requestBuilder.exclusiveStartKey(exclusiveStartKey);
        }

        Page<FriendRequest> page = friendRequestTable
                .query(requestBuilder.build())
                .stream()
                .findFirst()
                .orElse(null);

        if (page == null) {
            return PageResponse.<FriendRequestResponse>builder()
                    .items(List.of())
                    .nextCursor(null)
                    .build();
        }

        String nextCursor = null;
        if (page.lastEvaluatedKey() != null &&
                page.lastEvaluatedKey().get("sk") != null) {

            nextCursor = page.lastEvaluatedKey().get("sk").s();
        }

        return PageResponse.<FriendRequestResponse>builder()
                .items(page.items().stream().map(
                        i -> FriendRequestResponse.builder()
                                .id(i.getPk())
                                .name(i.getName())
                                .avatarUrl(i.getAvatarUrl())
                                .createdAt(i.getCreatedAt())
                                .build()
                ).toList())
                .nextCursor(nextCursor)
                .build();
    }

    private PageResponse<FriendRequestResponse> getPendingRequests(String userId, String cursor, int limit) {
        String gsiPartitionKey = "REQUEST#" + userId;
        DynamoDbIndex<FriendRequest> index = friendRequestTable.index("GSI_FriendRequest");

        QueryEnhancedRequest.Builder builder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(gsiPartitionKey)))
                .limit(limit);

        // Xử lý Pagination Cursor (Giả sử cursor là ID người gửi - gsi1sk)
        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = new HashMap<>();
            exclusiveStartKey.put("gsi1pk", AttributeValue.builder().s(gsiPartitionKey).build());
            exclusiveStartKey.put("gsi1sk", AttributeValue.builder().s(cursor).build());
            exclusiveStartKey.put("pk", AttributeValue.builder().s("USER#" + cursor.replace("USER#", "")).build());
            exclusiveStartKey.put("sk", AttributeValue.builder().s(gsiPartitionKey).build());

            builder.exclusiveStartKey(exclusiveStartKey);
        }

        // Thực thi Query trên Index
        SdkIterable<Page<FriendRequest>> results = index.query(builder.build());
        Page<FriendRequest> firstPage = results.iterator().next(); // Lấy trang đầu tiên

        if (firstPage == null || firstPage.items().isEmpty()) {
            return PageResponse.<FriendRequestResponse>builder().items(List.of()).build();
        }

        // Lấy cursor cho trang tiếp theo
        String nextCursor = null;
        if (firstPage.lastEvaluatedKey() != null) {
            nextCursor = firstPage.lastEvaluatedKey().get("gsi1sk").s();
        }

        return PageResponse.<FriendRequestResponse>builder()
                .items(firstPage.items().stream().map(
                        i -> FriendRequestResponse.builder()
                                .id(i.getPk())
                                .name(i.getName())
                                .avatarUrl(i.getAvatarUrl())
                                .createdAt(i.getCreatedAt())
                                .build()
                ).toList())
                .nextCursor(nextCursor)
                .build();
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
}
