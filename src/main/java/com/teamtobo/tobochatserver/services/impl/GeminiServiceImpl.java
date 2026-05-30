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

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GeminiServiceImpl implements GeminiService {

    @Value("${google.gemini.api-key}")
    private String apiKey;

    @Value("${google.gemini.url}")
    private String geminiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public Object generatePollJson(String userPrompt, String fileUrl) throws Exception {

        String systemPrompt = "Bạn là trợ lý tạo bình chọn. Trả về ĐÚNG định dạng JSON này, KHÔNG có text nào khác. " +
                "Cấu trúc: {\"question\": \"...\", \"options\": [{\"text\": \"...\"}, {\"text\": \"...\"}]}";

        List<Object> parts = new ArrayList<>();

        // Luôn luôn có phần Text (Prompt)
        parts.add(Map.of("text", systemPrompt + "\n\nYêu cầu của người dùng: " + userPrompt));

        // Nếu có đính kèm file, tải file từ S3 và chuyển thành Base64
        if (fileUrl != null && !fileUrl.trim().isEmpty()) {
            try {
                byte[] fileBytes = restTemplate.getForObject(fileUrl, byte[].class);
                if (fileBytes != null) {
                    String base64File = Base64.getEncoder().encodeToString(fileBytes);

                    // Thêm phần InlineData chứa nội dung PDF
                    parts.add(Map.of(
                            "inlineData", Map.of(
                                    "mimeType", "application/pdf",
                                    "data", base64File
                            )
                    ));
                }
            } catch (Exception e) {
                System.err.println("Lỗi khi tải file từ S3: " + e.getMessage());
            }
        }

        Map<String, Object> requestBody = Map.of(
                "contents", new Object[]{
                        Map.of("parts", parts)
                },
                "generationConfig", Map.of(
                        "responseMimeType", "application/json"
                )
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        String responseRaw = restTemplate.postForObject(geminiUrl, entity, String.class);

        JsonNode root = objectMapper.readTree(responseRaw);
        String generatedJsonText = root.path("candidates").get(0)
                .path("content").path("parts").get(0)
                .path("text").asText();

        return objectMapper.readValue(generatedJsonText, Object.class);
    }
}