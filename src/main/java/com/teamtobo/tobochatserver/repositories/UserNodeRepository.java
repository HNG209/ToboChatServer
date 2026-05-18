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

    @Query("MATCH (u1: User {id: $userId}), (u2: User {id: $otherId})" +
            "MERGE (u1) -[r:SEND_REQUEST]-> (u2)" +
            "ON CREATE SET r.createAt = timestamp()")
    void createFriendRequest (String userId, String otherId);

    @Query ("MATCH (u1: User {id: $userId}) -[r:SEND_REQUEST]-> (u2: User {id: $otherId})" +
            "DELETE r")
    void deleteFriendRequest (String userId, String otherId);

    @Query("MATCH (u1:User {id: $userId})-[r:FRIEND]-(u2:User {id: $otherId}) RETURN count(r) > 0")
    boolean isFriend(String userId, String otherId);

    @Query("MATCH (u1:User {id: $userId})-[r:SEND_REQUEST]->(u2:User {id: $otherId}) RETURN count(r) > 0")
    boolean hasSentRequest(String userId, String otherId);
}