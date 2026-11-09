package com.atwhis.agentdemo.ai.chat;

import com.atwhis.agentdemo.ai.tool.ToolResultRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationService conversations;
    private final ToolResultRecorder recorder;

    @DeleteMapping("/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clear(@PathVariable String conversationId) {
        conversations.clear(conversationId);
    }
    @GetMapping("/{conversationId}/turns/{turnId}")
    public ToolResultRecorder.Snapshot turn(@PathVariable String conversationId, @PathVariable String turnId) {
        return recorder.turn(turnId).filter(t -> t.conversationId.equals(conversationId))
                .map(ToolResultRecorder.Turn::snapshot)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
    }
}
