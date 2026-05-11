package com.teamtobo.tobochatserver.services.handlers;

import lombok.AllArgsConstructor;
import lombok.Data;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CallSessionManager {
    @Data
    private static class CallSession {
        private Instant startTime;
        private String initiatorId;
        private Set<String> participants = ConcurrentHashMap.newKeySet();
        private boolean isAnswered = false;
    }

    @Data
    @AllArgsConstructor
    public static class CallResult {
        private String status; // "ONGOING", "MISSED", "ENDED"
        private long duration;
        private String initiatorId;
    }

    private final Map<String, CallSession> activeCalls = new ConcurrentHashMap<>();

    // Khi có người yêu cầu gọi
    public void initCall(String roomId, String callerId) {
        CallSession session = activeCalls.computeIfAbsent(roomId, k -> new CallSession());

        // Chỉ gán người khởi tạo ở lần đầu tiên tạo phòng
        if (session.getInitiatorId() == null) {
            session.setInitiatorId(callerId);
        }
        session.getParticipants().add(callerId);
    }

    // Khi có người bắt máy
    public void markAsAnswered(String roomId, String userId) {
        CallSession session = activeCalls.get(roomId);
        if (session != null) {
            session.setAnswered(true);
            session.getParticipants().add(userId);
            if (session.getStartTime() == null) {
                session.setStartTime(Instant.now());
            }
        }
    }

    // Khi có người rời đi / từ chối
    public CallResult leaveCall(String roomId, String userId) {
        CallSession session = activeCalls.get(roomId);

        // Nếu session null nghĩa là người trước đó đã kết thúc cuộc gọi rồi -> Trả về null để Controller bỏ qua
        if (session == null) return null;

        // Xóa người này khỏi danh sách
        session.getParticipants().remove(userId);

        // Nếu phòng không còn ai (hoặc chỉ còn 1 người thì cũng không gọi được với ai)
        if (session.getParticipants().size() <= 1) {
            activeCalls.remove(roomId);
            String originalCallerId = session.getInitiatorId();

            if (!session.isAnswered()) {
                return new CallResult("MISSED", 0, originalCallerId);
            } else {
                long duration = Duration.between(session.getStartTime(), Instant.now()).getSeconds();
                return new CallResult("ENDED", duration, originalCallerId);
            }
        }

        // Nếu phòng vẫn còn > 1 người (Group Call), cuộc gọi vẫn tiếp diễn
        return new CallResult("ONGOING", 0, null);
    }

    public boolean joinExistingCall(String roomId, String userId) {
        CallSession session = activeCalls.get(roomId);

        if (session != null) {
            session.getParticipants().add(userId);

            // Nếu trước đó chưa ai bắt máy (VD: A gọi, chưa ai nghe, C bấm tham gia)
            // Thì đánh dấu là cuộc gọi đã được thiết lập (Answered)
            if (!session.isAnswered()) {
                session.setAnswered(true);
                session.setStartTime(Instant.now());
            }
            return true;
        }
        return false; // Cuộc gọi đã kết thúc hoặc không tồn tại
    }
}