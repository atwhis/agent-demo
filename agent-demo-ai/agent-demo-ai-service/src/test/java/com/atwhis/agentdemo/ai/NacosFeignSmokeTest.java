package com.atwhis.agentdemo.ai;

import com.atwhis.agentdemo.sales.client.SalesStatisticsClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Opt-in local integration smoke. It is skipped by normal package builds because
 * it requires the Compose Nacos/MySQL pair and a separately running sales service.
 */
@SpringBootTest(properties = {
        "agent-demo.ai.models.enabled=false",
        "spring.main.web-application-type=none"
})
@EnabledIfEnvironmentVariable(named = "RUN_NACOS_SMOKE", matches = "true")
class NacosFeignSmokeTest {

    @Autowired
    SalesStatisticsClient salesClient;

    @Test
    void discoversSalesServiceAndInvokesFeignContract() {
        LocalDate end = LocalDate.now();
        assertThat(salesClient.queryPopularProducts(end.minusDays(30), end, 5)).isNotNull();
    }
}
