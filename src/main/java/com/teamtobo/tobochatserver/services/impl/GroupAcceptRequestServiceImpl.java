package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.response.GroupAcceptRequestResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.entities.GroupAcceptRequest;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.InboxStatus;
import com.teamtobo.tobochatserver.entities.enums.MemberRole;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.GroupAcceptRequestService;
import com.teamtobo.tobochatserver.services.RoomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.*;
import software.amazon.awssdk.enhanced.dynamodb.model.*;

import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GroupAcceptRequestServiceImpl implements GroupAcceptRequestService {

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<GroupAcceptRequest> requestTable;
    private final DynamoDbTable<RoomMember> roomMemberTable;
    private final RoomService roomService;

    @Override
    public PageResponse<GroupAcceptRequestResponse> getInvites(
            String userId,
            String cursor,
            int limit
    ) {

        String pk = "USER#" + userId;

        QueryEnhancedRequest query = QueryEnhancedRequest.builder()
                .queryConditional(
                        QueryConditional.sortBeginsWith(
                                Key.builder()
                                        .partitionValue(pk)
                                        .sortValue("ROOM_ACCEPT#")
                                        .build()
                        )
                )
                .limit(limit)
                .build();

        List<GroupAcceptRequestResponse> items = requestTable.query(query)
                .items()
                .stream()
                .map(item -> GroupAcceptRequestResponse.builder()
                        .roomId(item.getRoomId())
                        .roomName(item.getRoomName())
                        .inviterId(item.getInviterId())
                        .build())
                .collect(Collectors.toList());

        return PageResponse.<GroupAcceptRequestResponse>builder()
                .items(items)
                .nextCursor(null)
                .build();
    }

    @Override
    public void respondInvite(String userId, String roomId, boolean accepted) {

        String pk = "USER#" + userId;
        String sk = "ROOM_ACCEPT#" + roomId;

        Key key = Key.builder()
                .partitionValue(pk)
                .sortValue(sk)
                .build();

        GroupAcceptRequest request = requestTable.getItem(key);

        if (request == null) {
            throw new AppException(ErrorCode.REQUEST_NOT_FOUND);
        }

        roomService.getRoomById(roomId, true);

        //check đã là member chưa
        RoomMember existing = roomMemberTable.getItem(
                Key.builder()
                        .partitionValue("ROOM#" + roomId)
                        .sortValue("MEMBER#" + userId)
                        .build()
        );

        if (existing != null) {
            requestTable.deleteItem(key);
            return;
        }

        TransactWriteItemsEnhancedRequest.Builder tx = TransactWriteItemsEnhancedRequest.builder();

        if (accepted) {
            String now = Instant.now().toString();

            RoomMember member = RoomMember.builder()
                    .pk("ROOM#" + roomId)
                    .sk("MEMBER#" + userId)
                    .role(MemberRole.MEMBER)
                    .status(InboxStatus.ACTIVE)
                    .roomName(request.getRoomName())
                    .lastActivityAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .statusTime("STATUS#ACTIVE#TIME#" + now)
                    .build();

            tx.addPutItem(roomMemberTable, member);
        }

        tx.addDeleteItem(requestTable, key);

        try {
            enhancedClient.transactWriteItems(tx.build());
        } catch (Exception e) {
            throw new AppException(ErrorCode.GROUP_INVITE_HANDLE_FAILED);
        }
    }
}