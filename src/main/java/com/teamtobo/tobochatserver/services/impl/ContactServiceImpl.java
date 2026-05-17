package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.response.FriendResponse;
import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;
import com.teamtobo.tobochatserver.entities.nodes.UserNode;
import com.teamtobo.tobochatserver.repositories.UserNodeRepository;
import com.teamtobo.tobochatserver.services.ContactService;
import com.teamtobo.tobochatserver.services.UserService;
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
                .items(currentFriends.stream().map(friend ->
                        FriendResponse.builder()
                                .id(friend.getId())
                                .name(userResponseMap.get(friend.getId()).getName())
                                .avatarUrl(userResponseMap.get(friend.getId()).getAvatarUrl())
                                .build()).toList())
                .nextCursor(hasNext ? String.valueOf(page + 1) : null)
                .build();
    }
}
