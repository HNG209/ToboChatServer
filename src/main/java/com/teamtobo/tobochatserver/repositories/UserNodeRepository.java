package com.teamtobo.tobochatserver.repositories;

import com.teamtobo.tobochatserver.entities.nodes.UserNode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface UserNodeRepository extends Neo4jRepository<UserNode, String> {
    @Query("MATCH (u:User {id: $userId})-[:FRIEND]-(f:User) " +
            "RETURN f " +
            "ORDER BY f.id ASC " +
            "SKIP :#{#pageable.offset} " +
            "LIMIT :#{#pageable.pageSize + 1}")
    List<UserNode> findAllFriends(String userId, Pageable pageable);

    @Query("MERGE (u1:User {id: $userId}) " +
            "MERGE (u2:User {id: $otherId}) " +
            "MERGE (u1)-[r:SEND_REQUEST]->(u2) " +
            "ON CREATE SET r.createAt = timestamp()")
    void createFriendRequest(String userId, String otherId);

    @Query("MERGE (u1:User {id: $userId}) " +
            "MERGE (u2:User {id: $otherId}) " +
            "MERGE (u1)-[r:FRIEND]->(u2) " +
            "ON CREATE SET r.createAt = timestamp()")
    void createFriend(String userId, String otherId);

    @Query ("MATCH (u1: User {id: $userId})-[r:SEND_REQUEST]-(u2: User {id: $otherId})" +
            "DELETE r")
    void deleteFriendRequest(String userId, String otherId);

    @Query ("MATCH (u1: User {id: $userId})-[r:FRIEND]-(u2: User {id: $otherId})" +
            "DELETE r")
    void deleteFriend(String userId, String otherId);

    @Query("MATCH (u1:User {id: $userId})" +
            "MATCH  (u2:User {id: $otherId})" +
            "OPTIONAL MATCH (u1)-[r:FRIEND]-(u2) " +
            "OPTIONAL MATCH (u1)-[s:SEND_REQUEST]->(u2) " +
            "OPTIONAL MATCH (u1)<-[p:SEND_REQUEST]-(u2) " +
            "RETURN " +
            "  CASE " +
            "    WHEN r IS NOT NULL THEN 'FRIEND' " +
            "    WHEN s IS NOT NULL THEN 'SENT' " +
            "    WHEN p IS NOT NULL THEN 'PENDING' " +
            "    ELSE 'STRANGER' " +
            "  END AS status")
    String getFriendStatus(String userId, String otherId);

    // Lời mời kết bạn ĐÃ GỬI (userId -> target)
    @Query("MATCH (u:User {id: $userId})-[r:SEND_REQUEST]->(target:User) " +
            "RETURN target " +
            "ORDER BY r.createAt DESC " +
            "SKIP :#{#pageable.offset} " +
            "LIMIT :#{#pageable.pageSize + 1}")
    List<UserNode> findSentRequests(String userId, Pageable pageable);

    // Lời mời kết bạn ĐÃ NHẬN / PENDING (sender -> userId)
    @Query("MATCH (u:User {id: $userId})<-[r:SEND_REQUEST]-(sender:User) " +
            "RETURN sender " +
            "ORDER BY r.createAt DESC " +
            "SKIP :#{#pageable.offset} " +
            "LIMIT :#{#pageable.pageSize + 1}")
    List<UserNode> findPendingRequests(String userId, Pageable pageable);
}