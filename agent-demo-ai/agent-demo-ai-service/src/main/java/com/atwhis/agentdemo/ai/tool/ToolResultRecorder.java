package com.atwhis.agentdemo.ai.tool;

import com.atwhis.agentdemo.ai.chart.ChartIntent;
import com.atwhis.agentdemo.ai.chat.IntentRouter;
import com.atwhis.agentdemo.ai.knowledge.KnowledgeService;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class ToolResultRecorder {
    private final Map<String, Turn> turns = new ConcurrentHashMap<>();

    public Turn open(String traceId, String conversationId) {
        Turn turn = new Turn(conversationId, traceId);
        turns.put(traceId, turn);
        return turn;
    }

    public Optional<Turn> turn(String traceId) {
        requireTraceId(traceId);
        return Optional.ofNullable(turns.get(traceId));
    }

    public void clear(String traceId) {
        Turn turn = turns.remove(traceId);
        if (turn != null) {
            turn.close("CANCELLED");
        }
    }

    public String begin(String traceId, String tool, String arguments) {
        return turn(traceId).orElseThrow(() -> new IllegalStateException("请求已结束")).begin(tool, arguments);
    }

    public void finish(String traceId, String callId, String output, Throwable error) {
        turn(traceId).ifPresent(turn -> turn.finish(callId, output, error));
    }

    public void capture(String traceId, String callId, ToolResult result) {
        turn(traceId).ifPresent(turn -> turn.capture(callId, result));
    }

    // Also used by fake gateways to supply a complete, independent sales call.
    public void record(String traceId, ToolResult result) {
        String id = begin(traceId, result.toolName(), "{}");
        capture(traceId, id, result);
        finish(traceId, id, "", null);
    }

    public Optional<ToolResult> find(String traceId) {
        return turn(traceId).flatMap(Turn::source).map(Call::businessResult);
    }

    public void recordChartIntent(String traceId, ChartIntent intent) {
        requireTraceId(traceId);
        Turn turn = turns.get(traceId);
        if (turn != null) {
            synchronized (turn) {
                if (turn.status.equals("RUNNING")) {
                    turn.intent = intent;
                }
            }
        }
    }

    public Optional<ChartIntent> findChartIntent(String traceId) {
        return turn(traceId).map(Turn::intent);
    }

    private void requireTraceId(String traceId) {
        if (traceId == null || traceId.isBlank()) {
            throw new IllegalArgumentException("缺少请求 traceId");
        }
    }

    public record Call(String conversationId, String turnId, String callId, String toolName, String arguments, Instant startedAt,
                       Instant endedAt, long elapsedMs, String status, String output,
                       String error, ToolResult businessResult, long completionSequence) {
        @JsonProperty("rowCount")
        public Integer rowCount() {
            return businessResult == null ? null : businessResult.rows().size();
        }
    }
    public record Snapshot(String conversationId, String turnId, String status, List<Call> calls,
                           IntentRouter.Route route, KnowledgeService.RetrievalTrace retrieval) { }

    public static final class Turn {
        public final String conversationId;
        public final String turnId;
        private String status = "RUNNING";
        private final Sinks.Empty<Void> cancellation = Sinks.empty();
        private final Map<String, Call> calls = new LinkedHashMap<>();
        private final Map<String, Long> starts = new HashMap<>();
        private long sequence;
        private long lastToolFinishedAt;
        private ChartIntent intent;
        private IntentRouter.Route route;
        private KnowledgeService.RetrievalTrace retrieval;

        public synchronized boolean recordRoute(IntentRouter.Route route) {
            if (!running()) {
                return false;
            }
            this.route = route;
            return true;
        }

        public synchronized boolean recordRetrieval(KnowledgeService.RetrievalTrace retrieval) {
            if (!running()) {
                return false;
            }
            this.retrieval = retrieval;
            return true;
        }

        private Turn(String conversationId, String turnId) {
            this.conversationId = conversationId;
            this.turnId = turnId;
        }

        public Mono<Void> cancellation() {
            return cancellation.asMono();
        }

        public synchronized boolean running() {
            return status.equals("RUNNING");
        }

        public synchronized String begin(String tool, String arguments) {
            if (!running()) {
                throw new IllegalStateException("请求已结束");
            }
            String id = UUID.randomUUID().toString();
            calls.put(id, new Call(conversationId, turnId, id, tool, arguments, Instant.now(), null, 0, "RUNNING", null, null, null, 0));
            starts.put(id, System.nanoTime());
            return id;
        }

        private synchronized void capture(String id, ToolResult result) {
            if (!running()) {
                return;
            }
            if (id == null) {
                String synthetic = begin(result.toolName(), "{}");
                capture(synthetic, result);
                finish(synthetic, "", null);
            } else if (calls.containsKey(id) && calls.get(id).status().equals("RUNNING")) {
                Call c = calls.get(id);
                calls.put(id, new Call(conversationId, turnId, id, c.toolName(), c.arguments(), c.startedAt(), null, 0,
                        "RUNNING", null, null, result, 0));
            }
        }

        private synchronized void finish(String id, String output, Throwable error) {
            Call c = calls.get(id);
            if (!running() || c == null || !c.status().equals("RUNNING")) {
                return;
            }
            lastToolFinishedAt = System.nanoTime();
            finishCall(c, lastToolFinishedAt, error == null ? "SUCCESS" : "ERROR", error == null ? output : null,
                    error == null ? null : error.getClass().getSimpleName(), c.businessResult());
        }

        private void finishCall(Call call, long endedAt, String outcome, String output, String error, ToolResult result) {
            long elapsed = (endedAt - starts.remove(call.callId())) / 1_000_000;
            calls.put(call.callId(), new Call(conversationId, turnId, call.callId(), call.toolName(), call.arguments(),
                    call.startedAt(), Instant.now(), elapsed, outcome, output, error, result, ++sequence));
            log.info("chat_timing turnId={} stage=sales.tool_call tool={} callId={} elapsed_ms={} status={}",
                    turnId, call.toolName(), call.callId(), elapsed, outcome);
        }

        public synchronized long finalAnswerStartedAt(long modelStartedAt) {
            return lastToolFinishedAt == 0 ? modelStartedAt : lastToolFinishedAt;
        }

        public synchronized Optional<Call> source() {
            return calls.values().stream().filter(c -> c.status().equals("SUCCESS") && c.businessResult() != null
                    && !c.businessResult().rows().isEmpty()).max(Comparator.comparingLong(Call::completionSequence));
        }

        public synchronized boolean hasSales() {
            return calls.values().stream().anyMatch(c -> c.status().equals("SUCCESS") && c.businessResult() != null);
        }

        public synchronized boolean failed() {
            return calls.values().stream().anyMatch(c -> !c.status().equals("SUCCESS"));
        }

        public synchronized ChartIntent intent() {
            return intent;
        }

        public synchronized void close(String outcome) {
            if (!running()) {
                return;
            }
            for (Call c : List.copyOf(calls.values())) {
                if (c.status().equals("RUNNING")) {
                    finishCall(c, System.nanoTime(), outcome.equals("CANCELLED") ? "CANCELLED" : "ERROR",
                            null, null, null);
                }
            }
            status = outcome;
            if (outcome.equals("CANCELLED")) {
                cancellation.tryEmitEmpty();
            }
        }

        public synchronized Snapshot snapshot() {
            return new Snapshot(conversationId, turnId, status, List.copyOf(calls.values()), route, retrieval);
        }
    }
}
