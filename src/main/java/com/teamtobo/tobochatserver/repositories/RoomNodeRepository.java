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

    record UserRoomStatus(String userId, String status) {}
    @Query("UNWIND $userIds AS uid " +
            "OPTIONAL MATCH (u:User {id: uid})-[r:ADDED|SENT|PENDING]-(rm:Room {id: $roomId}) " +
            "RETURN uid AS userId, " +
            "  CASE " +
            "    WHEN type(r) = 'ADDED' THEN 'ADDED' " +
            "    WHEN type(r) = 'SENT' THEN 'SENT' " +
            "    WHEN type(r) = 'PENDING' THEN 'PENDING' " +
            "    ELSE 'NOT_IN_GROUP' " +
            "  END AS status")
    List<UserRoomStatus> getMemberStatusesBatch(String roomId, List<String> userIds);

    record PendingRequestData(String targetUserId, String inviterId) {}
    @Query("MATCH (r:Room {id: $roomId})-[rel:PENDING]->(u:User) " +
            "RETURN u.id AS targetUserId, rel.inviterId AS inviterId " +
            "ORDER BY u.id ASC " +
            "SKIP :#{#pageable.offset} " +
            "LIMIT :#{#pageable.pageSize + 1}")
    List<PendingRequestData> findPendingRequests(String roomId, Pageable pageable);

    record AcceptRequestData(String roomId, String inviterId) {}
    @Query("MATCH (r:Room)-[rel:SENT]->(u:User {id: $userId}) " +
            "RETURN r.id AS roomId, rel.inviterId AS inviterId " +
            "ORDER BY r.id ASC " +
            "SKIP :#{#pageable.offset} " +
            "LIMIT :#{#pageable.pageSize + 1}")
    List<AcceptRequestData> findAcceptRequestsByUserId(String userId, Pageable pageable);

    // Lấy ID người mời của một yêu cầu chờ duyệt cụ thể
    @Query("MATCH (r:Room {id: $roomId})-[rel:PENDING]->(u:User {id: $userId}) " +
            "RETURN rel.inviterId")
    String getPendingInviterId(String roomId, String userId);
}