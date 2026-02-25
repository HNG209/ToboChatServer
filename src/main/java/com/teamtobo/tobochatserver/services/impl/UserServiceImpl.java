package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.request.FriendAcceptRequest;
import com.teamtobo.tobochatserver.dtos.request.UserUpdateRequest;
import com.teamtobo.tobochatserver.entities.FriendEntity;
import com.teamtobo.tobochatserver.entities.UserEntity;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final DynamoDbTable<UserEntity> userTable;
    private final DynamoDbTable<FriendEntity> friendTable;
    private final DynamoDbEnhancedClient enhancedClient;
    private final CognitoIdentityProviderClient cognitoClient;
    private final S3Client s3Client;

    @Value("${aws.cognito.userPoolId}")
    private String userPoolId;

    @Value("${aws.s3.bucketName}")
    private String bucketName;

    @Override
    public UserEntity getUserProfile(String userId) {
        // Query DynamoDB bằng PK (USER#id) và SK (PROFILE)
        UserEntity user = userTable.getItem(Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("PROFILE")
                .build());

        if (user == null) {
            throw new AppException(ErrorCode.USER_NOT_FOUND);
        }
        return user;
    }

    @Override
    public UserEntity updateUserProfile(String userId, UserUpdateRequest request) {
        UserEntity user = getUserProfile(userId);
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
//            user.setUpdatedAt(java.time.Instant.now().toString());
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
        UserEntity other = getUserProfile(otherId);

        // 2. Kiểm tra đã là bạn chưa
        FriendEntity existingFriend = friendTable.getItem(Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("FRIEND#" + otherId)
                .build());
        if (existingFriend != null) {
            throw new AppException(ErrorCode.ALREADY_FRIENDS);
        }

        // 3. Kiểm tra Friend Request đã gửi chưa (userId -> otherId)
        FriendEntity existingRequest = friendTable.getItem(Key.builder()
                .partitionValue("USER#" + userId)
                .sortValue("REQUEST#" + otherId)
                .build());
        if (existingRequest != null) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
        }

        // 4. Kiểm tra Friend Request đã nhận chưa (otherId -> userId)
        FriendEntity incomingRequest = friendTable.getItem(Key.builder()
                .partitionValue("USER#" + otherId)
                .sortValue("REQUEST#" + userId)
                .build());
        if (incomingRequest != null) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
        }

        // 5. Tạo friend request
        FriendEntity friendRequest = FriendEntity.builder()
                .pk("USER#" + userId)
                .sk("REQUEST#" + otherId)
                .name(other.getName())
                .build();

        friendTable.putItem(friendRequest);
    }

    // TODO: Thêm cancelFriendRequest

    @Override
    public void responseFriendRequest(String userId, FriendAcceptRequest request) {
        // 1. Định nghĩa các khóa để xác định bản ghi Request cũ
        Key requestKey = Key.builder()
                .partitionValue("USER#" + request.getFromUser())
                .sortValue("REQUEST#" + userId)
                .build();

        // TODO: Kiểm tra request có tồn tại không

        if (!request.isAccepted()) {
            // Trường hợp từ chối: Chỉ cần xóa yêu cầu, thay bằng cancelFriendRequest
            friendTable.deleteItem(requestKey);
            return;
        }

        // 2. Trường hợp chấp nhận: Cần lấy Profile của cả 2 để Denormalize tên
        UserEntity currentUser = getUserProfile(userId);
        UserEntity senderUser = getUserProfile(request.getFromUser());

        if (currentUser == null || senderUser == null) return;

        // 3. Thực hiện Transaction
        String now = Instant.now().toString();

        enhancedClient.transactWriteItems(b -> b
                // Xóa bản ghi Request
                .addDeleteItem(friendTable, requestKey)

                // Thêm bạn cho User hiện tại (userId)
                .addPutItem(friendTable, FriendEntity.builder()
                        .pk("USER#" + userId)
                        .sk("FRIEND#" + request.getFromUser())
                        .name(senderUser.getName())
                        .addedAt(now)
                        .build())

                // Thêm bạn cho người gửi (fromUser)
                .addPutItem(friendTable, FriendEntity.builder()
                        .pk("USER#" + request.getFromUser())
                        .sk("FRIEND#" + userId)
                        .name(currentUser.getName())
                        .addedAt(now)
                        .build())
        );
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
}
