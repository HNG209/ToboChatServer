package com.teamtobo.tobochatserver.utils;

public class Helper {
    public static String normalizeId(String id) {
        return id.split("#")[1]; // USER#001, FRIEND#001... -> 001
    }
}
