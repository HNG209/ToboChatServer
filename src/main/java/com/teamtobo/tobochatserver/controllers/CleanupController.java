package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.services.impl.CleanupService;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/cleanup")
@RequiredArgsConstructor
public class CleanupController {
    private final CleanupService cleanupService;

    @Operation(summary = "Clear tất cả trên DynamoDB trừ user")
    @DeleteMapping
    public ResponseEntity<Void> clearAll() {
        cleanupService.clearAll();
        return ResponseEntity.noContent().build();
    }
}
