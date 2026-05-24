package com.teamtobo.tobochatserver.annotations;

import com.teamtobo.tobochatserver.entities.enums.MemberPermission;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequireMemberPermission {
    MemberPermission value();
}