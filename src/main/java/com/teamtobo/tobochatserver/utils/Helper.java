package com.teamtobo.tobochatserver.utils;

import com.teamtobo.tobochatserver.exception.AppException;
import com.teamtobo.tobochatserver.exception.ErrorCode;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class Helper {
    public static String normalizeId(String id) {
        if (id == null || id.isEmpty()) {
            return id;
        }

        String[] parts = id.split("#");

        // Nếu không có dấu #, trả về nguyên vẹn chuỗi ban đầu.
        return parts[parts.length - 1];
    }
    public static String createDeterministicId(String id1, String id2) {
        if(id1.equals(id2)) return "";
        List<String> ids = new ArrayList<>(List.of(id1, id2));

        Collections.sort(ids);
        return ids.get(0) + "_" + ids.get(1);
    }

    public static boolean isDMRoom(String roomId) {
        if (!roomId.contains("_"))
            return false;

        String[] parts = roomId.split("_");

        return parts.length == 2;
    }

    public static String getOtherId(String userId, String roomId) {
        String otherId = null;
        if (roomId.contains("_")) {
            String[] parts = roomId.split("_");

            if (parts.length != 2) {
                throw new AppException(ErrorCode.ROOM_INVALID);
            }

            if (userId.equals(parts[0])) {
                otherId = parts[1];
            } else if (userId.equals(parts[1])) {
                otherId = parts[0];
            } else {
                throw new AppException(ErrorCode.NOT_IN_ROOM);
            }
        }
        return otherId;
    }
}
