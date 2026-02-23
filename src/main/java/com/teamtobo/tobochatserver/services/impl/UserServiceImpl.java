package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.request.UserUpdateRequest;
import com.teamtobo.tobochatserver.entities.UserEntity;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AdminUpdateUserAttributesRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AttributeType;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    private final DynamoDbTable<UserEntity> userTable;
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
