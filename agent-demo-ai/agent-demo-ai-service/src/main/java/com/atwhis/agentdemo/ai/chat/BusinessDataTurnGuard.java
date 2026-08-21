package com.atwhis.agentdemo.ai.chat;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BusinessDataTurnGuard {
    // This is a conservative semantic gate, not intent routing: the model still decides which Tool is appropriate.
    private static final List<String> BUSINESS_TERMS = List.of(
            "销售", "商品", "产品", "客户", "人员", "订单", "分类", "占比", "排行", "排名", "趋势", "购买", "金额", "数量");

    public boolean requiresSuccessfulTool(String message) {
        // A matching business question may finish successfully only when a Tool captured non-empty data.
        return message != null && BUSINESS_TERMS.stream().anyMatch(message::contains);
    }
}
