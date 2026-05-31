package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.services.UserPresenceService;
import lombok.AllArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.Set;

@Service
@AllArgsConstructor
public class UserPresenceServiceImpl implements UserPresenceService {
    private final StringRedisTemplate redisTemplate;
    private static final String ACTIVE_SESSIONS_ZSET = "presence:active_sessions";
    private static final String USER_TRACKING_KEY_PATTERN = "sessions:user:%s";
    private static final String LAST_SEEN_KEY_PATTERN = "last_seen:user:%s";
    private static final long TIMEOUT_MILLISECONDS = 60000;

    @Override
    public void receiveHeartbeat(String userId, String deviceId) {
        long currentTimestamp = Instant.now().toEpochMilli();
        String memberValue = userId + ":" + deviceId;
        String trackingKey = String.format(USER_TRACKING_KEY_PATTERN, userId);

        // Cập nhật vào ZSET tổng
        redisTemplate.opsForZSet().add(ACTIVE_SESSIONS_ZSET, memberValue, currentTimestamp);

        // Thêm thiết bị vào SET riêng của User
        redisTemplate.opsForSet().add(trackingKey, deviceId);
        // Cấp TTL cho SET này dài hơn 1 chút để nó tự hủy nếu user offline hẳn
        redisTemplate.expire(trackingKey, (TIMEOUT_MILLISECONDS / 1000) + 10, java.util.concurrent.TimeUnit.SECONDS);

        // Cập nhật Last Seen
        redisTemplate.opsForValue().set(String.format(LAST_SEEN_KEY_PATTERN, userId), String.valueOf(currentTimestamp));
    }

    @Override
    public boolean forceOffline(String userId, String deviceId) {
        String memberValue = userId + ":" + deviceId;
        String trackingKey = String.format(USER_TRACKING_KEY_PATTERN, userId);

        // Xóa khỏi ZSET tổng
        redisTemplate.opsForZSet().remove(ACTIVE_SESSIONS_ZSET, memberValue);

        // Xóa thiết bị khỏi SET riêng của User
        redisTemplate.opsForSet().remove(trackingKey, deviceId);

        // Kiểm tra xem user còn thiết bị nào không
        return checkFullyOffline(trackingKey);
    }

    @Scheduled(fixedRate = 10000)
    public void reapDeadSessions() {
        long cutoffTime = Instant.now().toEpochMilli() - TIMEOUT_MILLISECONDS;

        // Lấy danh sách rớt mạng
        Set<String> deadSessions = redisTemplate.opsForZSet().rangeByScore(ACTIVE_SESSIONS_ZSET, 0, cutoffTime);

        if (deadSessions != null && !deadSessions.isEmpty()) {
            for (String sessionInfo : deadSessions) {
                String[] parts = sessionInfo.split(":");
                String userId = parts[0];
                String deviceId = parts[1];

                String trackingKey = String.format(USER_TRACKING_KEY_PATTERN, userId);

                // Xóa thiết bị rớt mạng khỏi SET
                redisTemplate.opsForSet().remove(trackingKey, deviceId);

                // KỂM TRA HOÀN TOÀN OFFLINE
                if (checkFullyOffline(trackingKey)) {
                    System.out.println(">>> User [" + userId + "] ĐÃ HOÀN TOÀN OFFLINE (Rớt mạng)!");

                    // eventPublisher.publishEvent(new UserOfflineEvent(userId));
                }
            }

            // Dọn rác ZSET hàng loạt
            redisTemplate.opsForZSet().removeRangeByScore(ACTIVE_SESSIONS_ZSET, 0, cutoffTime);
        }
    }

    @Override
    public boolean isUserOnline(String userId) {
        String trackingKey = String.format(USER_TRACKING_KEY_PATTERN, userId);
        Long activeDeviceCount = redisTemplate.opsForSet().size(trackingKey);
        return activeDeviceCount != null && activeDeviceCount > 0;
    }

    private boolean checkFullyOffline(String trackingKey) {
        Long remainingDevices = redisTemplate.opsForSet().size(trackingKey);
        return remainingDevices == null || remainingDevices == 0;
    }
}