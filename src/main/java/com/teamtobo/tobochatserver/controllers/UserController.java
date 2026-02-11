package com.teamtobo.tobochatserver.controllers;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/users")
public class UserController {

    @GetMapping("/me")
    public Map<String, Object> getCurrentUser(@AuthenticationPrincipal Jwt jwt) {
        // Spring Boot tự động giải mã Token và đưa vào biến 'jwt'
        return Map.of(
                "userId", jwt.getSubject(), // Đây là cái 'sub' (ID định danh)
                "email", jwt.getClaim("email"),
                "token_id", jwt.getId()
        );
    }
}