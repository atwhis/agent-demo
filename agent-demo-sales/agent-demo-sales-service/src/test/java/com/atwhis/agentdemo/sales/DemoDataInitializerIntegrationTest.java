package com.atwhis.agentdemo.sales;

import com.atwhis.agentdemo.sales.client.TrendGranularity;
import com.atwhis.agentdemo.sales.service.DemoDataInitializer;
import com.atwhis.agentdemo.sales.service.OrderCalculationService;
import com.atwhis.agentdemo.sales.service.SalesAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class DemoDataInitializerIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderCalculationService calculator;
    @Autowired SalesAnalyticsService analytics;
    @BeforeEach void clean() { SalesDatabaseSupport.clear(jdbc); }

    @Test void initializesExpectedVolumesOnlyOnceAndFeedsEveryAnalysis() {
        DemoDataInitializer initializer = new DemoDataInitializer(jdbc, calculator);
        LocalDate today = LocalDate.of(2026, 8, 20);
        initializer.initialize(today);
        initializer.initialize(today);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_customer", Long.class)).isEqualTo(50);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_product_category", Long.class)).isEqualTo(9);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_product", Long.class)).isEqualTo(90);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_product_inventory", Long.class)).isEqualTo(90);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_sales_order", Long.class)).isEqualTo(1_000);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_sales_order_item", Long.class)).isBetween(1_500L, 3_000L);
        LocalDate start = today.minusDays(89);
        assertThat(analytics.customerRanking(start, today, 10)).isNotEmpty();
        assertThat(analytics.categoryShare(start, today)).hasSize(9);
        assertThat(analytics.popularProducts(start, today, 10)).isNotEmpty();
        assertThat(analytics.salesTrend(start, today, TrendGranularity.WEEK)).isNotEmpty();
        Long recent = jdbc.queryForObject("SELECT COUNT(*) FROM t_sales_order WHERE order_time >= ?", Long.class, today.minusDays(20).atStartOfDay());
        assertThat(recent).isGreaterThan(300L);
    }
}
