package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.dtos.request.UserUpdateRequest;
import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.entities.UserEntity;
import com.teamtobo.tobochatserver.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
}