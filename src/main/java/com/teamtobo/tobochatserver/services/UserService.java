package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.UserUpdateRequest;
import com.teamtobo.tobochatserver.entities.UserEntity;

public interface UserService {
    UserEntity getUserProfile(String userId);
    UserEntity updateUserProfile(String userId, UserUpdateRequest request);
}
