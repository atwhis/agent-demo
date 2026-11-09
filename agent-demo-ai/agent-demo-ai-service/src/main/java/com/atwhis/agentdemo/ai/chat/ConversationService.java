package com.atwhis.agentdemo.ai.chat;

import com.atwhis.agentdemo.ai.tool.ToolResultRecorder;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class ConversationService {
    private final ToolResultRecorder recorder;
    private final MessageWindowChatMemory memory = MessageWindowChatMemory.builder().maxMessages(20).build();
    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    public ToolResultRecorder.Turn begin(String cid) {
        while (true) {
            Session session = sessions.computeIfAbsent(cid, key -> new Session());
            synchronized (session) {
                if (session.cleared) {
                    continue;
                }
                if (session.active != null) {
                    throw new ConversationBusyException();
                }
                var turn = recorder.open(UUID.randomUUID().toString(), cid);
                session.active = turn;
                session.retained.addLast(turn.turnId);
                while (session.retained.size() > 10) {
                    recorder.clear(session.retained.removeFirst());
                }
                return turn;
            }
        }
    }

    public List<Message> history(String cid) {
        Session session = sessions.get(cid);
        if (session == null) {
            return List.of();
        }
        synchronized (session) {
            if (session.cleared) {
                return List.of();
            }
            List<Message> result = new ArrayList<>(memory.get(cid));
            // shortcut: UTF-8 bytes cap history conservatively; use provider token counting for larger contexts.
            while (result.size() >= 2 && result.stream()
                    .mapToInt(m -> m.getText().getBytes(StandardCharsets.UTF_8).length).sum() > 24000) {
                result.subList(0, 2).clear();
            }
            return List.copyOf(result);
        }
    }

    public List<Message> historyForTurn(String trace) {
        return recorder.turn(trace).map(t -> history(t.conversationId)).orElse(List.of());
    }

    public boolean complete(ToolResultRecorder.Turn turn, String question, String answer) {
        Session session = sessions.get(turn.conversationId);
        if (session == null) {
            return false;
        }
        synchronized (session) {
            if (session.active != turn || !turn.running()) {
                return false;
            }
            memory.add(turn.conversationId, List.of(new UserMessage(question), new AssistantMessage(answer)));
            session.active = null;
            turn.close("COMPLETED");
            return true;
        }
    }

    public void finish(ToolResultRecorder.Turn turn, String status) {
        Session session = sessions.get(turn.conversationId);
        if (session != null) {
            synchronized (session) {
                if (session.active == turn) {
                    session.active = null;
                    turn.close(status);
                }
            }
        }
    }

    public void clear(String cid) {
        Session session = sessions.get(cid);
        if (session != null) {
            synchronized (session) {
                if (session.cleared) {
                    return;
                }
                session.cleared = true;
                if (session.active != null) {
                    var turn = session.active;
                    session.active = null;
                    turn.close("CANCELLED");
                }
                memory.clear(cid);
                session.retained.forEach(recorder::clear);
                sessions.remove(cid, session);
            }
        }
    }

    private static final class Session {
        private ToolResultRecorder.Turn active;
        private final Deque<String> retained = new ArrayDeque<>();
        private boolean cleared;
    }

    static final class ConversationBusyException extends IllegalStateException {
        ConversationBusyException() {
            super("当前会话正在生成，请先停止或等待完成。");
        }
    }
}
