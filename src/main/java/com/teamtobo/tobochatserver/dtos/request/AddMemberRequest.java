package com.teamtobo.tobochatserver.dtos.request;

import lombok.Data;

import java.util.List;

@Data
public class AddMemberRequest {
    private List<String> targetUserIds;
}