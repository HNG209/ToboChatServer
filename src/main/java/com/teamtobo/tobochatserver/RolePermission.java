package com.teamtobo.tobochatserver;

import com.teamtobo.tobochatserver.entities.enums.MemberPermission;
import com.teamtobo.tobochatserver.entities.enums.MemberRole;

import java.util.Map;
import java.util.Set;

public class RolePermission {
    private static final Map<MemberRole, Set<MemberPermission>> map = Map.of(
            MemberRole.ADMIN, Set.of(MemberPermission.values()),

            MemberRole.VICE_ADMIN, Set.of(
                    MemberPermission.ADD_MEMBER,
                    MemberPermission.REMOVE_MEMBER,
                    MemberPermission.UPDATE_GROUP,
                    MemberPermission.SEND_MESSAGE
            ),

            MemberRole.MEMBER, Set.of(
                    MemberPermission.SEND_MESSAGE
            )
    );

    public static boolean hasPermission(MemberRole role, MemberPermission permission) {
        return map.getOrDefault(role, Set.of()).contains(permission);
    }
}
