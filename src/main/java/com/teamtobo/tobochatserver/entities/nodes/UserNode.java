package com.teamtobo.tobochatserver.entities.nodes;

import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("User")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserNode {
    @Id
    private String id;

    @Relationship(type = "FRIEND", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private Set<UserNode> friends = new HashSet<>();

    @Relationship(type = "SEND_REQUEST", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private Set<UserNode> sentRequests = new HashSet<>();
}