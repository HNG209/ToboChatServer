package com.teamtobo.tobochatserver.repositories;

import com.teamtobo.tobochatserver.entities.nodes.RoomNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoomNodeRepository extends Neo4jRepository<RoomNode, String> {

    // Hàm lấy trạng thái quan hệ
    @Query("OPTIONAL MATCH (u:User {id: $userId})-[r:ADDED|SENT|PENDING]->(rm:Room {id: $roomId}) " +
            "RETURN " +
            "  CASE " +
            "    WHEN type(r) = 'ADDED' THEN 'ADDED' " +
            "    WHEN type(r) = 'SENT' THEN 'SENT' " +
            "    WHEN type(r) = 'PENDING' THEN 'PENDING' " +
            "    ELSE 'NOT_IN_GROUP' " +
            "  END AS status")
    String getMemberStatus(String roomId, String userId);

    // 1. TRẠNG THÁI: ADDED (Xóa sạch mọi quan hệ cũ trước khi MERGE cạnh mới)
    @Query("MERGE (u:User {id: $userId}) " +
            "MERGE (r:Room {id: $roomId}) " +
            "WITH u, r " +
            "OPTIONAL MATCH (u)-[oldRel:ADDED|SENT|PENDING]->(r) " +
            "DELETE oldRel " +
            "WITH u, r " +
            "MERGE (u)-[:ADDED]->(r)")
    void addMember(String roomId, String userId);

    // 2. TRẠNG THÁI: SENT
    @Query("MERGE (u:User {id: $targetUserId}) " +
            "MERGE (r:Room {id: $roomId}) " +
            "WITH u, r " +
            "OPTIONAL MATCH (u)-[oldRel:ADDED|SENT|PENDING]->(r) " +
            "DELETE oldRel " +
            "WITH u, r " +
            "MERGE (u)-[rel:SENT]->(r) " +
            "SET rel.inviterId = $inviterId")
    void createSent(String roomId, String inviterId, String targetUserId);

    // 3. TRẠNG THÁI: PENDING
    @Query("MERGE (u:User {id: $targetUserId}) " +
            "MERGE (r:Room {id: $roomId}) " +
            "WITH u, r " +
            "OPTIONAL MATCH (u)-[oldRel:ADDED|SENT|PENDING]->(r) " +
            "DELETE oldRel " +
            "WITH u, r " +
            "MERGE (u)-[rel:PENDING]->(r) " +
            "SET rel.inviterId = $inviterId")
    void createPending(String roomId, String inviterId, String targetUserId);

    // 4. List danh sách room đã tham gia của user
    @Query("MATCH (u:User {id: $userId})-[:ADDED]->(r:Room) RETURN r.id")
    List<String> findRoomIdsByUserId(String userId);

    // 5. Xóa quan hệ
    @Query("MATCH (u:User {id: $userId})-[r]->(rm:Room {id: $roomId}) " +
            "DELETE r")
    void deleteRelation(String roomId, String userId);
}