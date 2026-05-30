package com.teamtobo.tobochatserver.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamtobo.tobochatserver.dtos.response.ApiResponse;
import com.teamtobo.tobochatserver.services.GeminiService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Gemini Test Controller")
@RestController
@RequestMapping("/ai/test")
@RequiredArgsConstructor
public class GeminiTestController {
    private final GeminiService geminiService;

    @GetMapping
    public ApiResponse<Object> generatePoll(
            @RequestParam String prompt
    ) throws Exception {
        return ApiResponse.<Object>builder()
                .result(geminiService.generatePollJson(prompt)).build();
    }
}
