package com.teamtobo.tobochatserver.services;

import com.teamtobo.tobochatserver.dtos.request.PollSubmitRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;

public interface PollService {
    MessageResponse createPoll(String senderId, String roomId, PollSubmitRequest request) throws Exception;
    MessageResponse updatePoll(String roomId, String pollId, PollSubmitRequest request, String userId) throws Exception;
    MessageResponse votePoll(String roomId, String pollId, String optionId, String userId) throws Exception;
}
