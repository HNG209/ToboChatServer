package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;
import com.teamtobo.tobochatserver.entities.User;
import com.teamtobo.tobochatserver.services.ContactService;
import com.teamtobo.tobochatserver.services.SearchService;
import com.teamtobo.tobochatserver.utils.Helper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.pagination.sync.SdkIterable;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.model.Page;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private final DynamoDbTable<User> userTable;
    private final ContactService contactService;
    @Override
    public PageResponse<UserResponse> findByEmail(String userId, String email, String cursor, int limit) {
        if (email == null || email.isEmpty()) {
            return PageResponse.<UserResponse>builder().items(List.of()).build();
        }

        // 1. Chuẩn hóa chuỗi tìm kiếm và xác định Shard (Partition Key của GSI)
        String searchPrefix = email.trim().toLowerCase();
        char firstChar = searchPrefix.toUpperCase().charAt(0);
        String shardPk = Character.isLetter(firstChar)
                ? "ENTITY#USER#" + firstChar
                : "ENTITY#USER#OTHER";

        // 2. Trỏ tới Index GSI_EmailSearch
        DynamoDbIndex<User> index = userTable.index("GSI_EmailSearch");

        // 3. Xây dựng Query Builder với điều kiện sortBeginsWith
        QueryEnhancedRequest.Builder builder = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.sortBeginsWith(
                        k -> k.partitionValue(shardPk).sortValue(searchPrefix)
                ))
                .limit(limit);

        // 4. Xử lý phân trang (Pagination)
        if (cursor != null && !cursor.isEmpty()) {
            Map<String, AttributeValue> exclusiveStartKey = new HashMap<>();
            exclusiveStartKey.put("searchPk", AttributeValue.builder().s(shardPk).build());
            exclusiveStartKey.put("searchSk", AttributeValue.builder().s(cursor).build());
            exclusiveStartKey.put("pk", AttributeValue.builder().s("USER#ID_PLACEHOLDER").build());
            exclusiveStartKey.put("sk", AttributeValue.builder().s("PROFILE").build());

            builder.exclusiveStartKey(exclusiveStartKey);
        }

        // 5. Truy vấn
        SdkIterable<Page<User>> results = index.query(builder.build());
        Iterator<Page<User>> iterator = results.iterator();

        if (!iterator.hasNext()) {
            return PageResponse.<UserResponse>builder().items(List.of()).build();
        }

        Page<User> page = iterator.next();

        // 6. Lấy cursor cho trang tiếp theo (email của user cuối cùng trong trang)
        String nextCursor = null;
        if (page.lastEvaluatedKey() != null && page.lastEvaluatedKey().containsKey("searchSk")) {
            nextCursor = page.lastEvaluatedKey().get("searchSk").s();
        }

        return PageResponse.<UserResponse>builder()
                .items(page.items().stream().map(
                        item -> UserResponse.builder()
                                .id(item.getPk())
                                .email(item.getEmail())
                                .avatarUrl(item.getAvatarUrl())
                                .name(item.getName())
                                .friendStatus(contactService.getFriendStatus(userId, Helper.normalizeId(item.getPk())))
                                .build()
                ).toList())
                .nextCursor(nextCursor)
                .build();
    }
}
