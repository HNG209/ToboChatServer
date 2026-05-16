package com.teamtobo.tobochatserver.repositories;

import com.teamtobo.tobochatserver.entities.nodes.UserNode;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface UserNodeRepository extends Neo4jRepository<UserNode, String> {
    @Query("MATCH (u:User {id: $userId})-[:FRIEND]-(f:User) " +
            "RETURN f " +
            "ORDER BY f.id ASC " +
            "SKIP :#{#pageable.offset} " +
            "LIMIT :#{#pageable.pageSize}")
    Slice<UserNode> findAllFriends(String userId, Pageable pageable);
}