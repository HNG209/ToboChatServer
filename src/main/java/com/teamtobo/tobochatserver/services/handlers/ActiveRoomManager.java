package com.teamtobo.tobochatserver.services.handlers;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ActiveRoomManager {

    // userId -> socketId -> roomIds
    private final Map<String, Map<String, Set<String>>> store = new ConcurrentHashMap<>();

    public void join(String userId, String socketId, String roomId) {
        store
                .computeIfAbsent(userId, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(socketId, k -> ConcurrentHashMap.newKeySet())
                .add(roomId);
    }

    public void leave(String userId, String socketId, String roomId) {
        Map<String, Set<String>> sockets = store.get(userId);
        if (sockets == null) return;

        Set<String> rooms = sockets.get(socketId);
        if (rooms != null) {
            rooms.remove(roomId);
        }
    }

    public void clearSocket(String userId, String socketId) {
        Map<String, Set<String>> sockets = store.get(userId);
        if (sockets == null) return;

        sockets.remove(socketId);

        // nếu user không còn socket nào → remove luôn user
        if (sockets.isEmpty()) {
            store.remove(userId);
        }
    }

    public boolean isActive(String userId, String roomId) {
        Map<String, Set<String>> sockets = store.get(userId);
        if (sockets == null) return false;

        return sockets.values().stream()
                .anyMatch(set -> set.contains(roomId));
    }
}
