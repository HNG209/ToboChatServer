package com.teamtobo.tobochatserver.services.impl;

import com.corundumstudio.socketio.SocketIOServer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamtobo.tobochatserver.dtos.PollData;
import com.teamtobo.tobochatserver.dtos.request.PollSubmitRequest;
import com.teamtobo.tobochatserver.dtos.response.MessageResponse;
import com.teamtobo.tobochatserver.entities.Message;
import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;
import com.teamtobo.tobochatserver.services.ChatDomainService;
import com.teamtobo.tobochatserver.services.ChatService;
import com.teamtobo.tobochatserver.services.PollService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.util.*;

@Service
@AllArgsConstructor
public class PollServiceImpl implements PollService {
    private final DynamoDbTable<Message> messageTable;
    private final ChatDomainService chatDomainService;
    private final ChatService chatService;
    private final ObjectMapper objectMapper;

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
    public MessageResponse updatePoll(String roomId, String pollId, PollSubmitRequest request, String userId) throws Exception {
        Key key = Key.builder().partitionValue("ROOM#" + roomId).sortValue("MSG#" + pollId).build();
        Message pollMessage = messageTable.getItem(key);

        if (pollMessage == null || pollMessage.getMetadata() == null) {
            throw new AppException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        // Tùy chọn: thêm logic check quyền tại đây (VD: userId có phải là pollMessage.getSenderId() không)

        String pollDataJson = pollMessage.getMetadata().get("pollData");
        PollData oldPollData = objectMapper.readValue(pollDataJson, PollData.class);

        List<PollData.PollOption> newOptions = new ArrayList<>();

        for (PollSubmitRequest.PollOptionDto reqOption : request.getOptions()) {
            if (reqOption.getId() != null && !reqOption.getId().trim().isEmpty()) {
                // Nếu là option cũ, tìm option cũ trong DB để copy lại danh sách người đã vote
                PollData.PollOption existingOption = oldPollData.getOptions().stream()
                        .filter(o -> o.getId().equals(reqOption.getId()))
                        .findFirst()
                        .orElse(null);

                if (existingOption != null) {
                    newOptions.add(PollData.PollOption.builder()
                            .id(existingOption.getId())
                            .text(reqOption.getText()) // Cập nhật text phòng trường hợp user sửa lỗi chính tả
                            .votedUserIds(existingOption.getVotedUserIds()) // Giữ nguyên lượt vote
                            .build());
                }
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
        oldPollData.setMultipleChoice(request.isMultipleChoice());
        oldPollData.setAllowAddOption(request.isAllowAddOption());
        oldPollData.setDeadline(request.getDeadline());

        String updatedPollDataJson = objectMapper.writeValueAsString(oldPollData);
        pollMessage.getMetadata().put("pollData", updatedPollDataJson);
        messageTable.putItem(pollMessage);

        MessageResponse response = chatService.buildMessageResponse(pollMessage);
        socketIOServer.getRoomOperations("room:" + roomId).sendEvent("poll_updated", response);

        return response;
    }

    @Override
    public MessageResponse votePoll(String roomId, String pollId, String optionId, String userId) throws Exception {
        Key key = Key.builder().partitionValue("ROOM#" + roomId).sortValue("MSG#" + pollId).build();
        Message pollMessage = messageTable.getItem(key);

        if (pollMessage == null || pollMessage.getMetadata() == null) {
            throw new AppException(ErrorCode.MESSAGE_NOT_FOUND);
        }

        String pollDataJson = pollMessage.getMetadata().get("pollData");
        PollData pollData = objectMapper.readValue(pollDataJson, PollData.class);

        boolean isMultipleChoice = pollData.isMultipleChoice();
        boolean hasVotedForThisOption = false;

        for (PollData.PollOption option : pollData.getOptions()) {
            // Nếu đây là phương án user vừa bấm
            if (option.getId().equals(optionId)) {
                if (option.getVotedUserIds().contains(userId)) {
                    // Nếu đã vote rồi -> Hủy vote (Toggle Off)
                    option.getVotedUserIds().remove(userId);
                } else {
                    // Nếu chưa vote -> Thêm vào (Toggle On)
                    option.getVotedUserIds().add(userId);
                    hasVotedForThisOption = true;
                }
            } else {
                // Nếu không cho phép chọn nhiều, phải xóa vote của user ở CÁC phương án khác
                if (!isMultipleChoice) {
                    option.getVotedUserIds().remove(userId);
                }
            }
        }

        String updatedPollDataJson = objectMapper.writeValueAsString(pollData);
        pollMessage.getMetadata().put("pollData", updatedPollDataJson);
        messageTable.putItem(pollMessage);

        MessageResponse response = chatService.buildMessageResponse(pollMessage);

        // Phát Event Socket báo hiệu Poll đã thay đổi dữ liệu
        socketIOServer.getRoomOperations("room:" + roomId).sendEvent("poll_updated", response);

        if (hasVotedForThisOption) {
            // chatDomainService.sendSystemMessage(roomId, userId, SystemAction.POLL_VOTED, Map.of("targetPollSk", pollSk));
        }

        return response;
    }
}
