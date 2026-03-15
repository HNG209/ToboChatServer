package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.dtos.response.FriendRequestResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.RoomResponse;
import com.teamtobo.tobochatserver.entities.FriendRequest;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.RoomService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.*;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<Room> roomTable;
    private final DynamoDbTable<RoomMember> roomMemberTable;
    @Override
    public void createRoom(RoomCreateRequest request, RoomType roomType) {
        // Loại bỏ các ID trùng lặp
        List<String> uniqueMembers = request.getMemberIds().stream()
                .distinct()
                .collect(Collectors.toList());

        String now = Instant.now().toString(); // Dùng chuẩn ISO 8601 cho thời gian

        switch (roomType) {
            case DM -> {
                // 1. Validate DM
                if (uniqueMembers.size() != 2) {
                    throw new AppException(ErrorCode.ROOM_INVALID);
                }

                // 2. Tạo Deterministic ID
                Collections.sort(uniqueMembers);
                String roomId = uniqueMembers.get(0) + "_" + uniqueMembers.get(1);

                // TODO: Kiểm tra xem phòng DM này đã tồn tại chưa (query bằng PK = ROOM#roomId và SK = METADATA)

                // DM room không cần tên cụ thể
                String roomName = "Direct Message";

                // 3. Ghi xuống DB
                saveRoomToDynamoDB(roomId, roomName, roomType, uniqueMembers, now);
            }

            case GROUP -> {
                // 1. Validate Group
                if (uniqueMembers.size() < 3) {
                    throw new AppException(ErrorCode.ROOM_INVALID);
                }
                if (request.getRoomName() == null || request.getRoomName().trim().isEmpty()) {
                    throw new AppException(ErrorCode.ROOM_INVALID);
                }

                // 2. Tạo Random ID cho nhóm
                String roomId = UUID.randomUUID().toString();

                // 3. Ghi xuống DB
                saveRoomToDynamoDB(roomId, request.getRoomName(), roomType, uniqueMembers, now);
            }
        }
    }

    @Override
    public PageResponse<RoomResponse> getJoinedRooms(String userId, String cursor, int limit) {
        String gsiPartitionKey = "MEMBER#" + userId;
        DynamoDbIndex<RoomMember> index = roomMemberTable.index("GSI_RoomMember");

        QueryEnhancedRequest.Builder builder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(gsiPartitionKey)))
                .limit(limit);

        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = new HashMap<>();
            exclusiveStartKey.put("memberPk", AttributeValue.builder().s(gsiPartitionKey).build());
            exclusiveStartKey.put("memberSk", AttributeValue.builder().s(cursor).build());
            exclusiveStartKey.put("pk", AttributeValue.builder().s("ROOM#" + cursor.replace("ROOM#", "")).build());
            exclusiveStartKey.put("sk", AttributeValue.builder().s(gsiPartitionKey).build());

            builder.exclusiveStartKey(exclusiveStartKey);
        }

        // Thực thi Query trên Index
        SdkIterable<Page<RoomMember>> results = index.query(builder.build());
        Page<RoomMember> firstPage = results.iterator().next(); // Lấy trang đầu tiên

        if (firstPage == null || firstPage.items().isEmpty()) {
            return PageResponse.<RoomResponse>builder().items(List.of()).build();
        }

        // Lấy cursor cho trang tiếp theo
        String nextCursor = null;
        if (firstPage.lastEvaluatedKey() != null) {
            nextCursor = firstPage.lastEvaluatedKey().get("memberSk").s();
        }

        return PageResponse.<RoomResponse>builder()
                .items(firstPage.items().stream().map(
                        i -> RoomResponse.builder()
                                .id(i.getPk()) // room id
                                .createdAt(i.getCreatedAt())
                                .build()
                ).toList())
                .nextCursor(nextCursor)
                .build();
    }

    @Override
    public List<String> getMembersByRoomId(String roomId) {
        // 1. Tạo điều kiện truy vấn
        Key searchKey = Key.builder()
                .partitionValue("ROOM#" + roomId)
                .sortValue("MEMBER#")
                .build();

        QueryConditional queryConditional = QueryConditional.sortBeginsWith(searchKey);

        // 2. Thực hiện query
        PageIterable<RoomMember> results = roomMemberTable.query(queryConditional);

        // 3. Lọc qua kết quả và chỉ lấy ra danh sách userId
        return results.items().stream()
                .map(RoomMember::getMemberId)
                .collect(Collectors.toList());
    }

    private void saveRoomToDynamoDB(String roomId, String roomName, RoomType type, List<String> memberIds, String now) {
        String pk = "ROOM#" + roomId;

        // 1. Khởi tạo Transaction
        TransactWriteItemsEnhancedRequest.Builder txBuilder = TransactWriteItemsEnhancedRequest.builder();

        // 2. Tạo đối tượng Room (METADATA)
        Room roomMetadata = Room.builder()
                .pk(pk)
                .roomName(roomName)
                .roomType(type)
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Thêm vào transaction (1)
        txBuilder.addPutItem(roomTable, roomMetadata);

        // 3. Tạo các đối tượng RoomMember
        for (int i = 0; i < memberIds.size(); i++) {
            String userId = memberIds.get(i);

            String role = (i == 0 && type == RoomType.GROUP) ? "ADMIN" : "MEMBER";

            RoomMember member = RoomMember.builder()
                    .pk(pk)
                    .sk("MEMBER#" + userId)
                    .role(role)
                    .roomName(roomName)
                    .lastActivityAt(now)
                    .createdAt(now)
                    .updatedAt(now)
                    .build();

            // Thêm vào transaction (2)
            txBuilder.addPutItem(roomMemberTable, member);
        }

        // 4. Transaction write (1,2)
        try {
            enhancedClient.transactWriteItems(txBuilder.build());
        } catch (Exception e) {
            throw new AppException(ErrorCode.ROOM_CREATE_ERROR);
        }
    }
}
