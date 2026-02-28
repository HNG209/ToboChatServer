package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.request.FriendAcceptRequest;
import com.teamtobo.tobochatserver.dtos.request.UserUpdateRequest;
import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.entities.FriendEntity;
import com.teamtobo.tobochatserver.entities.UserEntity;
import com.teamtobo.tobochatserver.services.UserService;
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

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;

    @GetMapping("/me")
    public ApiResponse<UserEntity> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();

        UserEntity user = userService.getUserProfile(userId);
        return ApiResponse.<UserEntity>builder()
                .result(user)
                .build();
    }

    @PutMapping(value = "/profile", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
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

    @PostMapping("/friends/{otherId}")
    public ResponseEntity<Void> sendFriendRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String otherId) {
        String userId = jwt.getSubject(); // sender

        userService.sendFriendRequest(userId, otherId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/friends/request/{otherId}")
    public ResponseEntity<Void> cancelFriendRequest(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String otherId) {
        String userId = jwt.getSubject();

        userService.cancelFriendRequest(userId, otherId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/friends")
    public ResponseEntity<Void> responseFriendRequest(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody FriendAcceptRequest request) {
        String userId = jwt.getSubject(); // receiver

        userService.responseFriendRequest(userId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/friendslist")
    public ApiResponse<PageResponse<FriendEntity>> getFriendList(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit
    ) {

        String userId = jwt.getSubject();

        return ApiResponse.<PageResponse<FriendEntity>>builder()
                .result(userService.getFriendList(userId, cursor, limit))
                .build();
    }

    @GetMapping("/friend-requests")
    public ApiResponse<PageResponse<FriendEntity>> getFriendRequestList(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit
    ) {

        return ApiResponse.<PageResponse<FriendEntity>>builder()
                .result(userService.getFriendRequestList(
                        jwt.getSubject(),
                        cursor,
                        limit
                ))
                .build();
    }

    @GetMapping("/pending-requests")
    public ApiResponse<PageResponse<FriendEntity>> getPendingRequestList(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "10") int limit
    ) {

        return ApiResponse.<PageResponse<FriendEntity>>builder()
                .result(userService.getPendingRequestList(
                        jwt.getSubject(),
                        cursor,
                        limit
                ))
                .build();
    }












}