package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.services.RoomMemberService;
import com.teamtobo.tobochatserver.services.RoomService;
import com.teamtobo.tobochatserver.services.UserService;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RoomMemberServiceImpl implements RoomMemberService {

    private final DynamoDbTable<RoomMember> roomMemberTable;
    private final RoomService roomService;
    private final UserService userService;

    /**
     * Lấy danh sách phòng đã tham gia của user với pagination
     * @param userId
     * @param cursor
     * @param limit
     * @return
     */
    @Override
    public PageResponse<RoomResponse> getJoinedRooms(String userId, String cursor, int limit) {
        String gsiPartitionKey = "MEMBER#" + userId;
        DynamoDbIndex<RoomMember> index = roomMemberTable.index("GSI_RoomMember");

        QueryEnhancedRequest.Builder builder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(gsiPartitionKey)))
                .limit(limit);

        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = new HashMap<>();
            exclusiveStartKey.put("roomPk", AttributeValue.builder().s(gsiPartitionKey).build());
            exclusiveStartKey.put("roomSk", AttributeValue.builder().s(cursor).build());
            exclusiveStartKey.put("pk", AttributeValue.builder().s("ROOM#" + cursor.replace("ROOM#", "")).build());
            exclusiveStartKey.put("sk", AttributeValue.builder().s(gsiPartitionKey).build());

            builder.exclusiveStartKey(exclusiveStartKey);
        }

        SdkIterable<Page<RoomMember>> results = index.query(builder.build());
        Page<RoomMember> firstPage = results.iterator().next();

        if (firstPage == null || firstPage.items().isEmpty()) {
            return PageResponse.<RoomResponse>builder().items(List.of()).build();
        }

        String nextCursor = null;
        if (firstPage.lastEvaluatedKey() != null) {
            nextCursor = firstPage.lastEvaluatedKey().get("roomSk").s();
        }

        return PageResponse.<RoomResponse>builder()
                .items(firstPage.items().stream().map(
                        i -> {
                            // Lấy metadata của phòng để lấy thông tin roomType
                            Room room = roomService.getRoomById(i.getPk());
                            RoomResponse.RoomResponseBuilder responseBuilder = RoomResponse.builder()
                                    .id(i.getPk())
                                    .roomType(room.getRoomType())
                                    .createdAt(i.getCreatedAt());
                            if (room.getRoomType() == RoomType.DM) {
                                List<String> memberIds = roomService.getMembersByRoomId(Helper.normalizeId(i.getPk()));
                                if (memberIds.size() <= 2) {
                                    String otherUserId = memberIds.stream()
                                            .filter(id -> !id.equals(userId))
                                            .findFirst()
                                            .orElse(null);

                                    if (otherUserId != null) {
                                        responseBuilder.roomName(userService.getUserProfile(otherUserId).getName());
                                    }
                                }
                            } else { // GROUP
                                List<String> memberIds = roomService.getMembersByRoomId(Helper.normalizeId(i.getPk()));
                                if (memberIds.size() > 2) {
                                    String groupName = memberIds.stream()
                                            .limit(3)
                                            .map(memberId -> userService.getUserProfile(memberId).getName())
                                            .collect(Collectors.joining(", "));
                                    responseBuilder.roomName(groupName);
                                } else {
                                    responseBuilder.roomName(i.getRoomName());
                                }
                            }

                            return responseBuilder.build();
                        }
                ).toList())
                .nextCursor(nextCursor)
                .build();
    }
}

