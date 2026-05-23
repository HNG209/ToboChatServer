package com.teamtobo.tobochatserver.services;

public interface DataMigrationService {
    void migrateFromDynamoToNeo4j();
    void clearAllNeo4jData();
}
