package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamtobo.tobochatserver.dtos.PollData;
import com.teamtobo.tobochatserver.dtos.request.PollGenerateRequest;
import com.teamtobo.tobochatserver.dtos.request.PollSubmitRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.dtos.response.UserResponse;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.entities.enums.SystemAction;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.*;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
@AllArgsConstructor
public class PollServiceImpl implements PollService {
    private final DynamoDbTable<Message> messageTable;
    private final ChatDomainService chatDomainService;
    private final ChatService chatService;
    private final UserService userService;
    private final ObjectMapper objectMapper;
    private final GeminiService geminiService;

    private final SocketIOServer socketIOServer;

    @Override
    public MessageResponse createPoll(String senderId, String roomId, PollSubmitRequest request) throws Exception {
        List<PollData.PollOption> initialOptions = new ArrayList<>();
        for (int i = 0; i < request.getOptions().size(); i++) {
            initialOptions.add(PollData.PollOption.builder()
                    .id("opt_" + i)
                    .text(request.getOptions().get(i).getText())
                    .build());
        }

        PollData pollData = PollData.builder()
                .question(request.getQuestion())
                .multipleChoice(request.isMultipleChoice())
                .allowAddOption(request.isAllowAddOption())
                .deadline(request.getDeadline())
                .options(initialOptions)
                .build();

        String pollDataJson = objectMapper.writeValueAsString(pollData);

        Map<String, String> metadata = new HashMap<>();
        metadata.put("widgetType", "POLL");
        metadata.put("pollData", pollDataJson); // Lưu toàn bộ data vào 1 key duy nhất

        return chatDomainService.sendWidgetMessage(roomId, senderId, metadata);
    }

    @Override
    public void generatePoll(String userId, PollGenerateRequest request) {
        CompletableFuture.runAsync(() -> {
            try {
                Object aiResult = geminiService.generatePollJson(request.getPrompt(), request.getFileUrl());

                socketIOServer.getRoomOperations(userId)
                        .sendEvent("poll_generated", aiResult);

            } catch (Exception e) {
                socketIOServer.getRoomOperations(userId)
                        .sendEvent("poll_generated_error", Map.of("message", "Lỗi gen AI"));
            }
        });
    }

    @Override
    public MessageResponse updatePoll(String roomId, String pollId, PollSubmitRequest request, String userId) throws Exception {
        Key key = Key.builder().partitionValue("ROOM#" + roomId).sortValue("MSG#" + pollId).build();
        Message pollMessage = messageTable.getItem(key);

        if (pollMessage == null || pollMessage.getMetadata() == null) {
            throw new AppException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        String pollDataJson = pollMessage.getMetadata().get("pollData");
        PollData oldPollData = objectMapper.readValue(pollDataJson, PollData.class);
        boolean isCreator = userId.equals(pollMessage.getSenderId());
        boolean canAddOption = oldPollData.isAllowAddOption();

        if (!isCreator && !canAddOption)
            throw new AppException(ErrorCode.INVALID_PERMISSION);

        List<PollData.PollOption> newOptions = new ArrayList<>();

        for (PollSubmitRequest.PollOptionDto reqOption : request.getOptions()) {
            if (reqOption.getId() != null && !reqOption.getId().trim().isEmpty()) {
                // Nếu là option cũ, tìm option cũ trong DB để copy lại danh sách người đã vote
                oldPollData.getOptions().stream()
                        .filter(o -> o.getId().equals(reqOption.getId()))
                        .findFirst()
                        .ifPresent(existingOption -> newOptions.add(PollData.PollOption.builder()
                                .id(existingOption.getId())
                                .text(reqOption.getText()) // Cập nhật text phòng trường hợp user sửa lỗi chính tả
                                .votedUserIds(existingOption.getVotedUserIds()) // Giữ nguyên lượt vote
                                .recentVoters(existingOption.getRecentVoters()) // Giữ nguyên danh sách đại diện
                                .build()));

            } else {
                // NẾU LÀ OPTION MỚI (Không có ID): Tạo ID mới và danh sách vote rỗng
                String newOptionId = "opt_" + UUID.randomUUID().toString().substring(0, 8);
                newOptions.add(PollData.PollOption.builder()
                        .id(newOptionId)
                        .text(reqOption.getText())
                        .votedUserIds(new ArrayList<>())
                        .build());
            }
        }

        oldPollData.setQuestion(request.getQuestion());
        oldPollData.setOptions(newOptions); // Danh sách option đã được phân loại ở trên
        oldPollData.setMultipleChoice(oldPollData.isMultipleChoice());
        oldPollData.setAllowAddOption(oldPollData.isAllowAddOption());
        oldPollData.setDeadline(oldPollData.getDeadline());

        if (isCreator) { // Chỉ được chỉnh sửa những thuộc tính này nếu là người tạo
            oldPollData.setMultipleChoice(request.isMultipleChoice());
            oldPollData.setAllowAddOption(request.isAllowAddOption());
            oldPollData.setDeadline(request.getDeadline());
        }

        String updatedPollDataJson = objectMapper.writeValueAsString(oldPollData);
        pollMessage.getMetadata().put("pollData", updatedPollDataJson);
        messageTable.putItem(pollMessage);

        MessageResponse response = chatService.buildMessageResponse(pollMessage);
        socketIOServer.getRoomOperations("room:" + roomId).sendEvent("poll_updated", response);

        chatDomainService.sendSystemMessage(roomId, userId, SystemAction.POLL_UPDATED, Map.of("pollId", pollId));

        return response;
    }

    @Override
    public MessageResponse votePoll(String roomId, String pollId, List<String> optionIds, String userId) throws Exception {
        Key key = Key.builder().partitionValue("ROOM#" + roomId).sortValue("MSG#" + pollId).build();
        Message pollMessage = messageTable.getItem(key);

        if (pollMessage == null || pollMessage.getMetadata() == null) {
            throw new AppException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        String pollDataJson = pollMessage.getMetadata().get("pollData");
        PollData pollData = objectMapper.readValue(pollDataJson, PollData.class);

        boolean hasChanges = false;

        for (PollData.PollOption option : pollData.getOptions()) {
            boolean shouldBeSelected = optionIds.contains(option.getId()); // FE bảo là có chọn ko?
            boolean isCurrentlySelected = option.getVotedUserIds().contains(userId); // Hiện tại DB đang ghi là gì?

            // NẾU FE bẩu chọn MÀ DB chưa có -> Thêm vào
            if (shouldBeSelected && !isCurrentlySelected) {
                option.getVotedUserIds().add(userId);
                syncRecentVoters(option);
                hasChanges = true;
            }
            // NẾU FE bẩu KHÔNG chọn MÀ DB đang có -> Xóa ra
            else if (!shouldBeSelected && isCurrentlySelected) {
                option.getVotedUserIds().remove(userId);
                syncRecentVoters(option);
                hasChanges = true;
            }
        }

        if (hasChanges) {
            String updatedPollDataJson = objectMapper.writeValueAsString(pollData);
            pollMessage.getMetadata().put("pollData", updatedPollDataJson);
            messageTable.putItem(pollMessage);

            MessageResponse response = chatService.buildMessageResponse(pollMessage);
            socketIOServer.getRoomOperations("room:" + roomId).sendEvent("poll_updated", response);

            // chatDomainService.sendSystemMessage(roomId, userId, SystemAction.POLL_VOTED, Map.of("pollId", pollId));

            return response;
        }

        return chatService.buildMessageResponse(pollMessage);
    }

    private void syncRecentVoters(PollData.PollOption option) {
        List<String> voters = option.getVotedUserIds();
        List<PollData.RecentVoter> recentList = new ArrayList<>();

        if (voters == null || voters.isEmpty()) {
            option.setRecentVoters(recentList);
            return;
        }

        List<String> idsToFetch = new ArrayList<>();
        int size = voters.size();

        idsToFetch.add(voters.get(size - 1)); // Người mới nhất luôn nằm ở cuối mảng
        if (size >= 2) {
            idsToFetch.add(voters.get(size - 2)); // Người mới thứ hai
        }

        Map<String, UserResponse> userResponseMap = userService.getUsersMapByIds(idsToFetch);

        for (String vId : idsToFetch) {
            UserResponse profile = (userResponseMap != null) ? userResponseMap.get(vId) : null;
            String avatarUrl = (profile != null) ? profile.getAvatarUrl() : null;

            recentList.add(PollData.RecentVoter.builder()
                    .id(vId)
                    .avatar(avatarUrl)
                    .build());
        }

        option.setRecentVoters(recentList);
    }
}
