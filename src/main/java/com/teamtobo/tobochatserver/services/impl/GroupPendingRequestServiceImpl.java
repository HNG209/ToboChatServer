package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.response.GroupPendingRequestResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.entities.GroupPendingRequest;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.MemberRole;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.GroupPendingRequestService;
import com.teamtobo.tobochatserver.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GroupPendingRequestServiceImpl implements GroupPendingRequestService {
    private final DynamoDbTable<GroupPendingRequest> pendingTable;
    private final DynamoDbTable<RoomMember> roomMemberTable;
    private final UserService userService;

    @Override
    public PageResponse<GroupPendingRequestResponse> getPending(String roomId, String userId, int limit) {

        String pk = "ROOM#" + roomId;

        // 1. check user có trong room không
        RoomMember member = roomMemberTable.getItem(
                Key.builder()
                        .partitionValue(pk)
                        .sortValue("MEMBER#" + userId)
                        .build()
        );

        if (member == null) {
            throw new AppException(ErrorCode.NOT_IN_ROOM);
        }

        // 2. chỉ ADMIN mới được xem pending
        if (member.getRole() != MemberRole.ADMIN) {
            throw new AppException(ErrorCode.INVALID_PERMISSION);
        }

        // 3. query pending
        QueryEnhancedRequest query = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(pk).build()
                ))
                .limit(limit)
                .build();

        List<GroupPendingRequestResponse> items = pendingTable.query(query)
                .items()
                .stream()
                .filter(item -> item.getUserId() != null && item.getRoomId() != null)
                .map(item -> GroupPendingRequestResponse.builder()
                        .user(userService.getUserProfile(item.getUserId()))
                        .inviter(userService.getUserProfile(item.getRequesterId()))
                        .roomId(item.getRoomId())
                        .roomName(item.getRoomName())
                        .build())
                .toList();

        return PageResponse.<GroupPendingRequestResponse>builder()
                .items(items)
                .nextCursor(null)
                .build();
    }
}