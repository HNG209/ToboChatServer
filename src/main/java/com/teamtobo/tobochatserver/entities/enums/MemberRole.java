package com.teamtobo.tobochatserver.entities.enums;

import lombok.Getter;

@Getter
public enum MemberRole {
    ADMIN(3), // trưởng nhóm
    VICE_ADMIN(2), // phó nhóm
    MEMBER(1); // thành viên

    private final int level;

    MemberRole(int level) {
        this.level = level;
    }

    public boolean isHigherThan(MemberRole targetRole) {
        return this.level > targetRole.getLevel();
    }
}
