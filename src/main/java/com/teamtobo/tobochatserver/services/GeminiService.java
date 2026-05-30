package com.teamtobo.tobochatserver.services;

import com.fasterxml.jackson.databind.JsonNode;

public interface GeminiService {
    Object generatePollJson(String userPrompt) throws Exception;
}
