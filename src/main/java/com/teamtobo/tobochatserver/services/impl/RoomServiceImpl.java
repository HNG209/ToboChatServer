package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.response.PresignedUploadResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;
import com.teamtobo.tobochatserver.entities.Room;
import com.teamtobo.tobochatserver.entities.RoomMember;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.RoomService;
import com.teamtobo.tobochatserver.utils.Helper;
import com.teamtobo.tobochatserver.utils.S3Helper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.*;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class RoomServiceImpl implements RoomService {
    private final DynamoDbTable<Room> roomTable;
    private final DynamoDbTable<RoomMember> roomMemberTable;
    private final DynamoDbEnhancedClient enhancedClient;
    private final S3Helper s3Helper;

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

    @Override
    public Room getRoomById(String roomId, boolean skipException) {
        Key key = Key.builder()
                .partitionValue("ROOM#" + Helper.normalizeId(roomId))
                .sortValue("METADATA")
                .build();

        Room room = roomTable.getItem(key);
        if(room == null && !skipException)
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);

        return room;
    }

    @Override
    public Map<String, Room> getRoomsMapByIds(List<String> roomIds) {
        if (roomIds == null || roomIds.isEmpty()) {
            return new HashMap<>();
        }

        // Loại bỏ các ID trùng lặp
        List<String> uniqueIds = roomIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());

        Map<String, Room> roomMap = new HashMap<>();

        int batchSize = 100;
        for (int i = 0; i < uniqueIds.size(); i += batchSize) {
            List<String> chunk = uniqueIds.subList(i, Math.min(uniqueIds.size(), i + batchSize));

            ReadBatch.Builder<Room> readBatchBuilder = ReadBatch.builder(Room.class)
                    .mappedTableResource(roomTable);

            chunk.forEach(id -> readBatchBuilder.addGetItem(Key.builder()
                    .partitionValue("ROOM#" + id)
                    .sortValue("METADATA")
                    .build()));

            BatchGetResultPageIterable batchResults = enhancedClient.batchGetItem(r -> r.addReadBatch(readBatchBuilder.build()));

            // Đọc kết quả của lô hiện tại và map vào kết quả
            batchResults.resultsForTable(roomTable).forEach(room -> roomMap.put(room.getRoomId(), room));
        }

        return roomMap;
    }

    @Override
    public PresignedUploadResponse getRoomAvatarUploadUrl(String roomId, String contentType) {
        // Validate room exists
        Room room = getRoomById(roomId, false);
        if (room == null) {
            throw new AppException(ErrorCode.ROOM_NOT_FOUND);
        }
        // Validate content type
        if (!"image/jpeg".equals(contentType) && !"image/png".equals(contentType)
            && !"image/jpg".equals(contentType) && !"image/webp".equals(contentType)
            && !"image/gif".equals(contentType)) {
            throw new AppException(ErrorCode.INVALID_AVATAR_URL);
        }
        return s3Helper.generatePresignedUploadUrl(roomId, contentType, "room-avatars");
    }
}
