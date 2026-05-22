package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.request.FriendAcceptRequest;
import com.teamtobo.tobochatserver.dtos.response.FriendRequestResponse;
import com.teamtobo.tobochatserver.dtos.response.FriendResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;
import com.teamtobo.tobochatserver.entities.enums.FriendRequestType;
import com.teamtobo.tobochatserver.entities.enums.FriendStatus;
import com.teamtobo.tobochatserver.entities.nodes.UserNode;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.repositories.UserNodeRepository;
import com.teamtobo.tobochatserver.services.ContactService;
import com.teamtobo.tobochatserver.services.UserService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContactServiceImpl implements ContactService {
    private final UserNodeRepository userNodeRepository;
    private final UserService userService;
    public PageResponse<FriendResponse> getFriends(String userId, String roomId, String nextCursor, int limit) {
        int page = Integer.parseInt(nextCursor);
        Pageable pageable = PageRequest.of(page, limit);

        List<UserNode> friends = userNodeRepository.findAllFriends(userId, pageable);

        boolean hasNext = friends.size() > pageable.getPageSize();
        List<UserNode> currentFriends = hasNext ? friends.subList(0, limit) : friends;

        List<String> userIds = currentFriends.stream().map(UserNode::getId).toList();

        Map<String, UserResponse> userResponseMap = userService.getUsersMapByIds(userIds);

        return PageResponse.<FriendResponse>builder()
                .items(currentFriends.stream().map(friend -> {
                    if (userResponseMap.get(friend.getId()) == null)
                        return FriendResponse.builder()
                            .id(friend.getId()).build();
                    return FriendResponse.builder()
                            .id(friend.getId())
                            .name(userResponseMap.get(friend.getId()).getName())
                            .avatarUrl(userResponseMap.get(friend.getId()).getAvatarUrl())
                            .build();
                }).toList())
                .nextCursor(hasNext ? String.valueOf(page + 1) : null)
                .build();
    }

    @Override
    @Transactional
    public void sendFriendRequest(String userId, String otherId) {
        if (userId.equals(otherId)) {
            throw new AppException(ErrorCode.CANNOT_ADD_SELF);
        }

        FriendStatus status = this.getFriendStatus(userId, otherId);

        if (status == FriendStatus.FRIEND) {
            throw new AppException(ErrorCode.ALREADY_FRIENDS);
        }

        if (status == FriendStatus.PENDING || status == FriendStatus.SENT) {
            throw new AppException(ErrorCode.FRIEND_REQUEST_ALREADY_SENT);
        }
        userNodeRepository.createFriendRequest(userId, otherId);
    }

    public void cancelFriendRequest(String userId, String otherId) {
        userNodeRepository.deleteFriendRequest(userId, otherId);
    }

    public FriendStatus getFriendStatus(String userId, String otherId) {
        if (userId.equals(otherId)) {
            return FriendStatus.SELF;
        }
        String statusStr = userNodeRepository.getFriendStatus(userId, otherId);

        if (statusStr == null) {
            return FriendStatus.STRANGER;
        }
        return FriendStatus.valueOf(statusStr);
    }

    public PageResponse<FriendRequestResponse> getFriendRequests(FriendRequestType type, String userId, String cursor, int limit) {
        int page = (cursor == null || cursor.isEmpty()) ? 0 : Integer.parseInt(cursor);
        Pageable pageable = PageRequest.of(page, limit);

        List<UserNode> requestNodes;
        if (type == FriendRequestType.SENT) {
            requestNodes = userNodeRepository.findSentRequests(userId, pageable);
        } else {
            requestNodes = userNodeRepository.findPendingRequests(userId, pageable);
        }

        boolean hasNext = requestNodes.size() > pageable.getPageSize();
        List<UserNode> currentNodes = hasNext ? requestNodes.subList(0, limit) : requestNodes;

        List<String> userIds = currentNodes.stream().map(UserNode::getId).toList();
        Map<String, UserResponse> userResponseMap = userService.getUsersMapByIds(userIds);

        List<FriendRequestResponse> items = currentNodes.stream().map(node -> {
            UserResponse user = userResponseMap.get(node.getId());

            return FriendRequestResponse.builder()
                    .id(node.getId())
                    .name(user != null ? user.getName() : "Người dùng ToboChat")
                    .avatarUrl(user != null ? user.getAvatarUrl() : null)
                    .build();
        }).toList();

        return PageResponse.<FriendRequestResponse>builder()
                .items(items)
                .nextCursor(hasNext ? String.valueOf(page + 1) : null)
                .build();
    }

    @Override
    public void responseFriendRequest(String userId, FriendAcceptRequest request) {
        String senderId = request.getFromUser();

        FriendStatus currentStatus = this.getFriendStatus(userId, senderId);
        if (currentStatus != FriendStatus.PENDING) return;

        userNodeRepository.deleteFriendRequest(senderId, userId);

        if (!request.isAccepted()) return;

        userNodeRepository.createFriend(senderId, userId);
    }
}
