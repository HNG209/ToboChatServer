package com.teamtobo.tobochatserver.services.impl;

import com.teamtobo.tobochatserver.dtos.events.UserPresenceUpdateEvent;
import com.teamtobo.tobochatserver.dtos.response.UserPresenceResponse;
import com.teamtobo.tobochatserver.entities.enums.UserPresenceStatus;
import com.teamtobo.tobochatserver.services.UserPresenceService;
import lombok.AllArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

@Service
@AllArgsConstructor
public class UserPresenceServiceImpl implements UserPresenceService {
    private final StringRedisTemplate redisTemplate;
    private static final String ACTIVE_SESSIONS_ZSET = "presence:active_sessions";
    private static final String USER_TRACKING_KEY_PATTERN = "sessions:user:%s";
    private static final String LAST_SEEN_KEY_PATTERN = "last_seen:user:%s";
    private static final long TIMEOUT_MILLISECONDS = 60000;
    private final ApplicationEventPublisher eventPublisher;

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

        // Publish cho tất cả bạn bè của user trạng thái mới nhất
        eventPublisher.publishEvent(new UserPresenceUpdateEvent(userId, UserPresenceStatus.ONLINE, currentTimestamp));
    }

    @Override
    public void forceOffline(String userId, String deviceId) {
        String memberValue = userId + ":" + deviceId;
        String trackingKey = String.format(USER_TRACKING_KEY_PATTERN, userId);

        // Xóa khỏi ZSET tổng
        redisTemplate.opsForZSet().remove(ACTIVE_SESSIONS_ZSET, memberValue);

        // Xóa thiết bị khỏi SET riêng của User
        redisTemplate.opsForSet().remove(trackingKey, deviceId);

        // Kiểm tra xem user còn thiết bị nào không và broadcast trạng thái mới nhất cho bạn bè
        if(checkFullyOffline(trackingKey))
            eventPublisher.publishEvent(new UserPresenceUpdateEvent(userId, UserPresenceStatus.OFFLINE, getLastSeen(userId)));
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
                    System.out.println("User [" + userId + "] ĐÃ HOÀN TOÀN OFFLINE (Rớt mạng)!");

                    eventPublisher.publishEvent(new UserPresenceUpdateEvent(userId, UserPresenceStatus.OFFLINE, getLastSeen(userId)));
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

    private Long getLastSeen(String userId) {
        String lastSeenStr = redisTemplate.opsForValue().get(String.format(LAST_SEEN_KEY_PATTERN, userId));
        return lastSeenStr != null ? Long.parseLong(lastSeenStr) : 0L;
    }

    @Override
    public UserPresenceResponse getUserPresenceStatus(String userId) {
        boolean isOnline = isUserOnline(userId);
        Long lastSeen = getLastSeen(userId);

        return UserPresenceResponse.builder()
                .status(isOnline ? UserPresenceStatus.ONLINE : UserPresenceStatus.OFFLINE)
                .lastSeen(!isOnline ? lastSeen : null) // chỉ trả về last seen khi offline
                .build();
    }

    @Override
    public Map<String, UserPresenceResponse> getUsersPresenceStatuses(List<String> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<String> trackingKeys = userIds.stream()
                .map(id -> String.format(USER_TRACKING_KEY_PATTERN, id))
                .toList();

        List<String> lastSeenKeys = userIds.stream()
                .map(id -> String.format(LAST_SEEN_KEY_PATTERN, id))
                .toList();

        // 2. BATCH LẤY LAST SEEN (Dùng mGET cho kiểu String)
        // Lệnh này trả về list giá trị tương ứng 1-1 với list key truyền vào
        List<String> lastSeenValues = redisTemplate.opsForValue().multiGet(lastSeenKeys);

        // 3. BATCH KIỂM TRA ONLINE (Dùng Pipelining cho lệnh SCARD của kiểu Set)
        // Pipelining giúp gửi N lệnh SCARD lên Redis trong CÙNG 1 connection duy nhất
        List<Object> onlineStatuses = redisTemplate.executePipelined(new SessionCallback<Object>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                StringRedisTemplate stringTemplate = (StringRedisTemplate) operations;
                for (String key : trackingKeys) {
                    stringTemplate.opsForSet().size(key); // Lệnh này đưa vào queue, chưa chạy ngay
                }
                return null;
            }
        });

        // 4. Lắp ráp dữ liệu trả về
        Map<String, UserPresenceResponse> resultMap = new HashMap<>();

        for (int i = 0; i < userIds.size(); i++) {
            String userId = userIds.get(i);

            // Xử lý isOnline từ kết quả Pipelining
            Object scardResult = onlineStatuses.get(i);
            boolean isOnline = scardResult != null && ((Long) scardResult) > 0;

            // Xử lý lastSeen từ kết quả multiGet
            String lastSeenStr = lastSeenValues != null ? lastSeenValues.get(i) : null;
            Long lastSeen = lastSeenStr != null ? Long.parseLong(lastSeenStr) : 0L;

            resultMap.put(userId, UserPresenceResponse.builder()
                    .status(isOnline ? UserPresenceStatus.ONLINE : UserPresenceStatus.OFFLINE)
                    .lastSeen(lastSeen)
                    .build());
        }

        return resultMap;
    }

    private boolean checkFullyOffline(String trackingKey) {
        Long remainingDevices = redisTemplate.opsForSet().size(trackingKey);
        return remainingDevices == null || remainingDevices == 0;
    }
}