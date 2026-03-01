package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.request.FriendAcceptRequest;
import com.teamtobo.tobochatserver.dtos.request.MfaConfirmRequest;
import com.teamtobo.tobochatserver.dtos.request.MfaInitRequest;
import com.teamtobo.tobochatserver.dtos.request.UserUpdateRequest;
import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.entities.FriendEntity;
import com.teamtobo.tobochatserver.dtos.response.MfaInitResponse;
import com.teamtobo.tobochatserver.entities.UserEntity;
import com.teamtobo.tobochatserver.services.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Tag(name = "User", description = "APIs quản lý người dùng")
@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @Operation(summary = "Thông tin người dùng hiện tại")
    @GetMapping("/me")
    public ApiResponse<UserEntity> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();

        UserEntity user = userService.getUserProfile(userId);
        return ApiResponse.<UserEntity>builder()
                .result(user)
                .build();
    }

    @Operation(summary = "Cập nhật thông tin người dùng hiện tại")
    @PutMapping(value = "/me", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UserEntity> updateProfile(
            @AuthenticationPrincipal Jwt jwt,
            @RequestPart(value = "name", required = false) String name,
            @RequestPart(value = "avatar", required = false) MultipartFile avatar) {

        String userId = jwt.getSubject();

        UserEntity updatedUser = userService.updateUserProfile(userId, UserUpdateRequest.builder()
                .name(name)
                .avatar(avatar)
                .build());
        return ResponseEntity.ok(updatedUser);
    }

    @Operation(summary = "Danh sách bạn bè của người dùng hiện tại")
    @GetMapping("/me/friends")
    public ApiResponse<PageResponse<FriendEntity>> getMyFriendList(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit
    ) {

        String userId = jwt.getSubject();

        return ApiResponse.<PageResponse<FriendEntity>>builder()
                .result(userService.getFriends(userId, cursor, limit))
                .build();
    }

    @PostMapping("/mfa/init")
    public ResponseEntity<MfaInitResponse> initMFA(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody MfaInitRequest request
            ) {
        String userId = jwt.getSubject();

        MfaInitResponse response = userService.initEnableMFA(userId, request.getPassword());

        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/confirm")
    public ResponseEntity<Void> confirmMFA(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody MfaConfirmRequest request
            ) {
        String userId = jwt.getSubject();

        userService.confirmEnableMFA(userId, request.getOtp());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/mfa")
    public ResponseEntity<Void> disableMFA(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody MfaInitRequest request
            ) {
        String userId = jwt.getSubject();

        userService.disableMFA(userId, request.getPassword());
        return ResponseEntity.noContent().build();
    }

}