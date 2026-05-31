package com.teamtobo.tobochatserver.utils;

import com.teamtobo.tobochatserver.dtos.response.PresignedUploadResponse;
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
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;


import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Helper {
    @Value("${aws.s3.bucketName}")
    private String bucketName;
    private final S3Presigner s3Presigner;

    public PresignedUploadResponse generatePresignedUploadUrl(String id, String contentType, String folder) {
        // Validate Content-Type
        Set<String> allowedTypes = Set.of(
                "image/png",
                "image/jpeg",
                "image/jpg",
                "image/webp",
                "image/gif"
        );

        if (!allowedTypes.contains(contentType)) {
            throw new AppException(ErrorCode.INVALID_AVATAR_URL);
        }

        String extension = contentType.substring(contentType.indexOf("/") + 1);

        // fix mấy case đặc biệt
        if (extension.equals("jpeg")) extension = "jpg";
        if (extension.equals("svg+xml")) extension = "svg";

        /* Generate key with format: folder/id/uuid.extension */
        String key = folder + "/" + id + "/" + UUID.randomUUID() + "." + extension;

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
                .signatureDuration(Duration.ofMinutes(5))
                .putObjectRequest(putObjectRequest)
                .build();

        PresignedPutObjectRequest presignedRequest = s3Presigner.presignPutObject(presignRequest);

        return PresignedUploadResponse.builder()
                .url(presignedRequest.url().toString())
                .build();
    }

    public PresignedUploadResponse generatePresignedUploadUrl(String userId, String contentType) {
        return generatePresignedUploadUrl(userId, contentType, "avatars");
    }
}

