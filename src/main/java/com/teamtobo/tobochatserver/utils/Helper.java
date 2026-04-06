package com.teamtobo.tobochatserver.utils;

public class Helper {
    public static String normalizeId(String id) {
        if (id == null || id.isEmpty()) {
            return id;
        }

        String[] parts = id.split("#");

        // Nếu không có dấu #, trả về nguyên vẹn chuỗi ban đầu.
        return parts.length > 1 ? parts[1] : id;
    }
}
