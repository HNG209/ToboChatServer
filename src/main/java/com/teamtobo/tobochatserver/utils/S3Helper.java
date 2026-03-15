package com.teamtobo.tobochatserver.utils;

import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetUrlRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Helper {
    @Value("${aws.s3.bucketName}")
    private String bucketName;

    private final S3Client s3Client;
    public String uploadFileToS3(String userId, MultipartFile file) {
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
}
