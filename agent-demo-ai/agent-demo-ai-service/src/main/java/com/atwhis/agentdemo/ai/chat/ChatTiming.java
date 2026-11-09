package com.atwhis.agentdemo.ai.chat;

import lombok.extern.slf4j.Slf4j;

import java.util.function.Supplier;

@Slf4j
public final class ChatTiming {
    private ChatTiming() { }

    public static <T> T measure(String turnId, String stage, Supplier<T> operation) {
        long startedAt = System.nanoTime();
        String status = "ERROR";
        try {
            T result = operation.get();
            status = "OK";
            return result;
        } finally {
            finish(turnId, stage, startedAt, status);
        }
    }

    public static void finish(String turnId, String stage, long startedAt, String status) {
        log.info("chat_timing turnId={} stage={} elapsed_ms={} status={}",
                turnId, stage, elapsedMs(startedAt), status);
    }

    public static long elapsedMs(long startedAt) {
        return (System.nanoTime() - startedAt) / 1_000_000;
    }
}
