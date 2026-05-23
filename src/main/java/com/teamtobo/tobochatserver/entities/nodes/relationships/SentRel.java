package com.teamtobo.tobochatserver.entities.nodes.relationships;

import com.teamtobo.tobochatserver.entities.nodes.UserNode;
import lombok.*;
import org.springframework.data.neo4j.core.schema.RelationshipId;
import org.springframework.data.neo4j.core.schema.RelationshipProperties;
import org.springframework.data.neo4j.core.schema.TargetNode;

@RelationshipProperties
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class SentRel {
    @RelationshipId
    private Long id;

    @TargetNode
    private UserNode user; // Đầu bên kia của cạnh (User)

    private String inviterId; // ID người mời
}