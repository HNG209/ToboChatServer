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
        private String callerId;
        private Set<String> participants = ConcurrentHashMap.newKeySet();
        private boolean isAnswered = false;
    }

    @Data
    @AllArgsConstructor
    public static class CallResult {
        private String status; // "ONGOING", "MISSED", "ENDED"
        private long duration;
    }

    private final Map<String, CallSession> activeCalls = new ConcurrentHashMap<>();

    // Khi có người yêu cầu gọi
    public void initCall(String roomId, String callerId) {
        // Chỉ tạo mới nếu phòng chưa tồn tại cuộc gọi nào
        activeCalls.computeIfAbsent(roomId, k -> new CallSession())
                .getParticipants().add(callerId);
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

        // LOGIC CHÍNH: Nếu phòng không còn ai (hoặc chỉ còn 1 người thì cũng không gọi được với ai)
        if (session.getParticipants().size() <= 1) {
            activeCalls.remove(roomId);

            if (!session.isAnswered()) {
                return new CallResult("MISSED", 0);
            } else {
                long duration = Duration.between(session.getStartTime(), Instant.now()).getSeconds();
                return new CallResult("ENDED", duration);
            }
        }

        // Nếu phòng vẫn còn > 1 người (Group Call), cuộc gọi vẫn tiếp diễn
        return new CallResult("ONGOING", 0);
    }
}