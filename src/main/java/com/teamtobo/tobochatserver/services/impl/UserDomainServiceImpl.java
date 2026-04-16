package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.request.FriendAcceptRequest;
import com.teamtobo.tobochatserver.dtos.request.RoomCreateRequest;
import com.teamtobo.tobochatserver.entities.Friend;
import com.teamtobo.tobochatserver.entities.FriendRequest;
import com.teamtobo.tobochatserver.entities.User;
import com.teamtobo.tobochatserver.entities.enums.RoomType;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.RoomDomainService;
import com.teamtobo.tobochatserver.services.UserDomainService;
import com.teamtobo.tobochatserver.services.UserService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import java.time.Instant;
import java.util.List;

@Service
@AllArgsConstructor
public class UserDomainServiceImpl implements UserDomainService {
    private final DynamoDbTable<Friend> friendTable;
    private final DynamoDbTable<FriendRequest> friendRequestTable;
    private final DynamoDbEnhancedClient enhancedClient;
    private final RoomDomainService roomDomainService;
    private final UserService userService;
    @Override
    public void responseFriendRequest(String userId, FriendAcceptRequest request) {
        // Định nghĩa các khóa để xác định Request cũ
        Key requestKey = Key.builder()
                .partitionValue("USER#" + request.getFromUser())
                .sortValue("REQUEST#" + userId)
                .build();

        // Kiểm tra request có tồn tại không
        FriendRequest existingRequest = friendRequestTable.getItem(requestKey);
        if (existingRequest == null) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_NOT_FOUND);
        }

        if (!request.isAccepted()) {
            // Xóa yêu cầu nếu từ chối
            friendRequestTable.deleteItem(requestKey);
            return;
        }

        User currentUser = userService.getUserById(userId);
        User senderUser = userService.getUserById(request.getFromUser());

        String now = Instant.now().toString();

        enhancedClient.transactWriteItems(b -> b
                // Xóa bản ghi Request
                .addDeleteItem(friendTable, requestKey)

                // Thêm bạn cho User hiện tại (userId)
                .addPutItem(friendTable, Friend.builder()
                        .pk("USER#" + userId)
                        .sk("FRIEND#" + request.getFromUser())
                        .name(senderUser.getName())
                        .avatarUrl(senderUser.getAvatarUrl())
                        .addedAt(now)
                        .build())

                // Thêm bạn cho người gửi (fromUser)
                .addPutItem(friendTable, Friend.builder()
                        .pk("USER#" + request.getFromUser())
                        .sk("FRIEND#" + userId)
                        .name(currentUser.getName())
                        .avatarUrl(currentUser.getAvatarUrl())
                        .addedAt(now)
                        .build())
        );

        roomDomainService.createRoom(
                userId,
                RoomCreateRequest.builder()
                        .memberIds(List.of(userId, request.getFromUser()))
                        .build(),
                RoomType.DM
        );
    }
}
