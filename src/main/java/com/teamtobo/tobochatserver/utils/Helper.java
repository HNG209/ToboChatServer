package com.teamtobo.tobochatserver.utils;

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
        return parts.length > 1 ? parts[1] : id;
    }
    public static String createDeterministicId(String id1, String id2) {
        if(id1.equals(id2)) return "";
        List<String> ids = new ArrayList<>(List.of(id1, id2));

        Collections.sort(ids);
        return ids.get(0) + "_" + ids.get(1);
    }
}
