package com.teamtobo.tobochatserver;

import com.teamtobo.tobochatserver.entities.enums.MemberPermission;
import com.teamtobo.tobochatserver.entities.enums.MemberRole;

import java.util.Map;
import java.util.Set;

public class RolePermission {
    // Định nghĩa mỗi Role có Permission gì
    private static final Map<MemberRole, Set<MemberPermission>> map = Map.of(
            MemberRole.ADMIN, Set.of(MemberPermission.values()),

            MemberRole.VICE_ADMIN, Set.of(
                    MemberPermission.ADD_MEMBER,
                    MemberPermission.REMOVE_MEMBER,
                    MemberPermission.APPROVE_MEMBER,
                    MemberPermission.UPDATE_GROUP,
                    MemberPermission.SEND_MESSAGE,
                    MemberPermission.LEAVE_GROUP
            ),

            MemberRole.MEMBER, Set.of(
                    MemberPermission.SEND_MESSAGE,
                    MemberPermission.ADD_MEMBER,
                    MemberPermission.LEAVE_GROUP
            )
    );

    public static boolean hasPermission(MemberRole role, MemberPermission permission) {
        return map.getOrDefault(role, Set.of()).contains(permission);
    }
}
