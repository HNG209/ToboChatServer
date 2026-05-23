package com.teamtobo.tobochatserver.repositories;

import com.teamtobo.tobochatserver.entities.nodes.RoomNode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomNodeRepository extends Neo4jRepository<RoomNode, String> {
    @Query("OPTIONAL MATCH (u:User {id: $userId})-[r:ADDED|SENT|PENDING]-(rm:Room {id: $roomId}) " +
            "RETURN " +
            "  CASE " +
            "    WHEN type(r) = 'ADDED' THEN 'ADDED' " +
            "    WHEN type(r) = 'SENT' THEN 'SENT' " +
            "    WHEN type(r) = 'PENDING' THEN 'PENDING' " +
            "    ELSE 'NOT_IN_GROUP' " +
            "  END AS status")
    String getMemberStatus(String roomId, String userId);

    @Query("MERGE (u:User {id: $userId}) " +
            "MERGE (r:Room {id: $roomId}) " +
            "WITH u, r " +
            "OPTIONAL MATCH (u)-[oldRel:ADDED|SENT|PENDING]-(r) " +
            "DELETE oldRel " +
            "WITH u, r " +
            "MERGE (u)-[:ADDED]->(r)")
    void addMember(String roomId, String userId);

    @Query("MERGE (u:User {id: $targetUserId}) " +
            "MERGE (r:Room {id: $roomId}) " +
            "WITH u, r " +
            "OPTIONAL MATCH (u)-[oldRel:ADDED|SENT|PENDING]-(r) " +
            "DELETE oldRel " +
            "WITH u, r " +
            "MERGE (r)-[rel:SENT]->(u) " +
            "SET rel.inviterId = $inviterId")
    void createSentRequest(String roomId, String inviterId, String targetUserId);

    @Query("MERGE (u:User {id: $targetUserId}) " +
            "MERGE (r:Room {id: $roomId}) " +
            "WITH u, r " +
            "OPTIONAL MATCH (u)-[oldRel:ADDED|SENT|PENDING]-(r) " +
            "DELETE oldRel " +
            "WITH u, r " +
            "MERGE (r)-[rel:PENDING]->(u) " +
            "SET rel.inviterId = $inviterId")
    void createPendingRequest(String roomId, String inviterId, String targetUserId);

    // Danh sách room đã tham gia của user
    @Query("MATCH (u:User {id: $userId})-[:ADDED]-(r:Room) RETURN r.id " +
            "SKIP :#{#pageable.offset} " +
            "LIMIT :#{#pageable.pageSize + 1}")
    List<String> findRoomIdsByUserId(String userId, Pageable pageable);

    @Query("MATCH (u:User {id: $userId})-[r]-(rm:Room {id: $roomId}) " +
            "DELETE r")
    void deleteRelationship(String roomId, String userId);
}