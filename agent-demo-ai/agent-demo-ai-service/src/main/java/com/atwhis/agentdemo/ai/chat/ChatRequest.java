package com.atwhis.agentdemo.ai.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @Size(max = 100) String conversationId,
        @NotBlank @Size(max = 100) String modelId,
        @NotBlank @Size(max = 4000) String message) { }
