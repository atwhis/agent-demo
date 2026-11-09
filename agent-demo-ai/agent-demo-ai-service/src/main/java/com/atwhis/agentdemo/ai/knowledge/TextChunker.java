package com.atwhis.agentdemo.ai.knowledge;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;
import java.util.regex.Pattern;

final class TextChunker {
    private static final Pattern HEADING = Pattern.compile(
            "^\\s*(?:#{1,6}\\s+.+|第[〇零一二三四五六七八九十百0-9]+[章节篇部]\\s*.*)$");
    private static final Pattern BLOCK = Pattern.compile("^\\s*(?:第.+?条|[0-9]+[.、)]|[一二三四五六七八九十]+、).*");
    private final KnowledgeSettings settings;
    private final ToIntFunction<String> embedding;
    private final ToIntFunction<String> reranking;

    TextChunker(KnowledgeSettings settings, ToIntFunction<String> embedding, ToIntFunction<String> reranking) {
        this.settings = settings;
        this.embedding = embedding;
        this.reranking = reranking;
    }

    record Chunk(String sectionPath, String text) { }

    List<Chunk> split(String title, String text) {
        List<Chunk> result = new ArrayList<>();
        String section = "";
        StringBuilder sectionText = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            if (HEADING.matcher(line).matches()) {
                appendSection(result, title, section, sectionText.toString());
                sectionText.setLength(0);
                // Keep the complete heading in the body; cap the repeated contextual label.
                section = line.strip();
                section = section.substring(0, section.offsetByCodePoints(0,
                        Math.min(80, section.codePointCount(0, section.length()))));
            }
            if (BLOCK.matcher(line).matches() && !sectionText.isEmpty()) {
                sectionText.append('\n');
            }
            sectionText.append(line).append('\n');
        }
        appendSection(result, title, section, sectionText.toString());
        return result;
    }

    private void appendSection(List<Chunk> chunks, String title, String section, String body) {
        String prefix = title + (section.isEmpty() ? "" : " / " + section);
        // The full title/heading also lives in metadata and the original text.
        while (!prefix.isEmpty() && !fits(prefix + "\n", settings.targetTokens() / 2)) {
            prefix = prefix.substring(0, prefix.offsetByCodePoints(prefix.length(), -1));
        }
        prefix += "\n";
        String pending = "";
        for (String block : body.strip().split("\n\\s*\n")) {
            if (block.isBlank()) {
                continue;
            }
            String combined = pending.isEmpty() ? block : pending + "\n\n" + block;
            if (fits(prefix + combined, settings.targetTokens())) {
                pending = combined;
                continue;
            }
            if (!pending.isEmpty()) {
                chunks.add(new Chunk(section, prefix + pending));
                pending = "";
            }
            if (fits(prefix + block, settings.maxTokens())) {
                pending = block;
            } else {
                splitLong(chunks, section, prefix, block);
            }
        }
        if (!pending.isEmpty()) {
            chunks.add(new Chunk(section, prefix + pending));
        }
    }

    private void splitLong(List<Chunk> chunks, String section, String prefix, String block) {
        int[] points = block.codePoints().toArray();
        int start = 0;
        while (start < points.length) {
            int low = start + 1, high = Math.min(points.length, start + 4096), end = start;
            // Search Unicode boundaries using the actual tokenizer counts, never truncated encodings.
            while (low <= high) {
                int middle = (low + high) / 2;
                if (fits(prefix + new String(points, start, middle - start), settings.maxTokens())) {
                    end = middle;
                    low = middle + 1;
                } else {
                    high = middle - 1;
                }
            }
            if (end == start) {
                throw new IllegalArgumentException("切片预算不足，请检查切片配置。");
            }
            if (end < points.length) {
                for (int i = end; i > start + (end - start) / 2; i--) {
                    if ("。！？；.!?;\n".indexOf(points[i - 1]) >= 0
                            && fits(prefix + new String(points, start, i - start), settings.maxTokens())) {
                        end = i;
                        break;
                    }
                }
            }
            chunks.add(new Chunk(section, prefix + new String(points, start, end - start)));
            int next = end;
            if (end < points.length && settings.overlapTokens() > 0) {
                while (next > start + (end - start + 1) / 2
                        && embedding.applyAsInt(new String(points, next - 1, end - next + 1)) <= settings.overlapTokens()) {
                    next--;
                }
            }
            start = next;
        }
    }

    private boolean fits(String text, int limit) {
        return embedding.applyAsInt(text) <= limit && reranking.applyAsInt(text) <= limit;
    }
}
