package com.teamtobo.tobochatserver.services;

public interface GeminiService {
    Object generatePollJson(String userPrompt, String fileUrl) throws Exception;
}
