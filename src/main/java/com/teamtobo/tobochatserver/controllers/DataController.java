package com.teamtobo.tobochatserver.controllers;

import com.teamtobo.tobochatserver.services.DataMigrationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Migrator", description = "Migrator API")
@RestController
@RequestMapping("/migrator")
@RequiredArgsConstructor
public class DataController {
    private final DataMigrationService dataMigrationService;

    @Operation(summary = "Migrate quan hệ sang Neo4j")
    @PostMapping
    public ResponseEntity<Void> migrateData() {
        dataMigrationService.migrateFromDynamoToNeo4j();
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Clear tất cả trên Neo4j")
    @DeleteMapping
    public ResponseEntity<Void> clearAll() {
        dataMigrationService.clearAllNeo4jData();
        return ResponseEntity.noContent().build();
    }
}
