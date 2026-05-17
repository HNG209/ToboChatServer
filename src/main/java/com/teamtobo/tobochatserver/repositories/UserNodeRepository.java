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
}