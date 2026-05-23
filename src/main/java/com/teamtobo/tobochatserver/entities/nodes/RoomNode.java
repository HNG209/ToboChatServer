package com.teamtobo.tobochatserver.entities.nodes;

import com.teamtobo.tobochatserver.entities.nodes.relationships.SentRel;
import com.teamtobo.tobochatserver.entities.nodes.relationships.PendingRel;
import lombok.*;
import org.springframework.data.neo4j.core.schema.Id;
import org.springframework.data.neo4j.core.schema.Node;
import org.springframework.data.neo4j.core.schema.Relationship;

import java.util.HashSet;
import java.util.Set;

@Node("Room")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RoomNode {
    @Id
    private String id;

    // 1. Vào thẳng phòng (Cạnh ADDED từ User đến Room)
    @Relationship(type = "ADDED", direction = Relationship.Direction.INCOMING)
    @Builder.Default
    private Set<UserNode> addedUsers = new HashSet<>();

    // 2. Được mời vào phòng (Cạnh SENT chứa inviterId từ User đến Room)
    @Relationship(type = "SENT", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private Set<SentRel> sentUsers = new HashSet<>();

    // 3. Chờ duyệt vào phòng (Cạnh PENDING chứa inviterId từ User đến Room)
    @Relationship(type = "PENDING", direction = Relationship.Direction.OUTGOING)
    @Builder.Default
    private Set<PendingRel> pendingUsers = new HashSet<>();
}