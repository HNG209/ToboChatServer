package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.PollCreateRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;

public interface PollService {
    MessageResponse createPoll(String senderId, String roomId, PollCreateRequest request) throws Exception;
    MessageResponse addOptionToPoll(String roomId, String pollSk, String newOptionText, String userId) throws Exception;
    MessageResponse votePoll(String roomId, String pollId, String optionId, String userId) throws Exception;
}
