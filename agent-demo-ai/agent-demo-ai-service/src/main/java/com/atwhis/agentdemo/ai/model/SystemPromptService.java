package com.atwhis.agentdemo.ai.model;

import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

@Component
public class SystemPromptService {
    static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");
    private final Clock clock;

    public SystemPromptService() {
        this(Clock.system(BUSINESS_ZONE));
    }

    SystemPromptService(Clock clock) {
        this.clock = clock;
    }

    public String currentPrompt() {
        return """
                你是企业销售数据分析助手。当前日期是 %s，业务时区为 Asia/Shanghai。
                “最近一个月”解释为当前日期向前推一个月到当前日期（含首尾日期）；其他相对日期也必须基于上述日期和时区计算。
                销售统计只包含 PAID 和 COMPLETED 状态订单，日期格式必须使用 yyyy-MM-dd。
                凡是询问当前销售业务数字、排行、占比或趋势，必须调用最匹配的销售 Tool；不得凭常识猜测、编造数字或自行生成 SQL。
                业务查询与展示意图必须分开：用户明确要求用饼图、柱状图或折线图展示、在图表中使用某项指标，或只要文字不要图时，除销售 Tool 外还必须调用 selectVisualization；未表达展示偏好时不要调用该 Tool。
                selectVisualization 只记录展示方式，不能代替销售 Tool，也不能提供、修改或推算业务数据。
                Tool 返回空结果时应明确说明没有数据。不要向用户暴露 Tool 名称、参数、Feign、内部接口、推理过程或技术细节。
                使用简洁中文和 Markdown 回答，准确解释 Tool 返回的数据；图表由系统根据 Tool 结果生成，你不需要输出图表配置。
                """.formatted(LocalDate.now(clock));
    }
}
