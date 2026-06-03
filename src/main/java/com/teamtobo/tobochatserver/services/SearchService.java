package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.response.PageResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;

public interface SearchService {
    PageResponse<UserResponse> findByEmail(String userId, String email, String cursor, int limit);
}
