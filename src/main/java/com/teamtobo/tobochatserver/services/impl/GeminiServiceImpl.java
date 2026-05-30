package com.teamtobo.tobochatserver.services.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.teamtobo.tobochatserver.services.GeminiService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
public class GeminiServiceImpl implements GeminiService {

    @Value("${google.gemini.api-key}")
    private String apiKey;

    @Value("${google.gemini.url}")
    private String geminiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Object generatePollJson(String userPrompt) throws Exception {
        String url = geminiUrl + "?key=" + apiKey;

        String systemPrompt = "Bạn là trợ lý tạo bình chọn. Trả về ĐÚNG định dạng JSON này, KHÔNG có text nào khác. " +
                "Cấu trúc: {\"question\": \"...\", \"options\": [{\"text\": \"...\"}, {\"text\": \"...\"}]}";

        // Xây dựng payload gọi Gemini
        Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                        Map.of("parts", new Object[]{
                                Map.of("text", systemPrompt + "\n\nYêu cầu của người dùng: " + userPrompt)
                        })
                },
                "generationConfig", Map.of(
                        "responseMimeType", "application/json"
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String responseRaw = restTemplate.postForObject(url, entity, String.class);

        JsonNode root = objectMapper.readTree(responseRaw);
        String generatedJsonText = root.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();

        return objectMapper.readValue(generatedJsonText, Object.class);
    }
}