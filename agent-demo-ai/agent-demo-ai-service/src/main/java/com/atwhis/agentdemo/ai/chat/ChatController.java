package com.atwhis.agentdemo.ai.chat;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {
    private final ChatService chatService;

    /**
     * POST is used because model selection and the user message belong in a JSON body.
     * WebFlux keeps the connection open and writes each domain event as one SSE event.
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@Valid @RequestBody ChatRequest request) {
        return chatService.stream(request)
                // The browser receives stable event names (delta/chart/done/error), not raw model chunks.
                .map(event -> ServerSentEvent.builder(event.data()).event(event.type()).build());
    }
}
