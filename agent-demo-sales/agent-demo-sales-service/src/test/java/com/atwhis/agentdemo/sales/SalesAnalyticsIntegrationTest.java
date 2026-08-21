package com.atwhis.agentdemo.sales;

import com.atwhis.agentdemo.sales.client.TrendGranularity;
import com.atwhis.agentdemo.sales.service.SalesAnalyticsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SalesAnalyticsIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @Autowired SalesAnalyticsService analytics;

    @BeforeEach void setUp() { SalesDatabaseSupport.clear(jdbc); SalesDatabaseSupport.insertAnalyticsFixture(jdbc); }

    @Test void excludesIneffectiveOrdersAndRanksCustomers() {
        var result = analytics.customerRanking(LocalDate.of(2026,8,1), LocalDate.of(2026,8,4), 10);
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().customerName()).isEqualTo("Bob");
        assertThat(result.getFirst().purchaseQuantity()).isEqualTo(3);
        assertThat(result.getFirst().salesAmount()).isEqualByComparingTo("60.00");
        assertThat(result.get(1).customerName()).isEqualTo("Alice");
        assertThat(result.get(1).purchaseQuantity()).isEqualTo(3);
        assertThat(result.get(1).salesAmount()).isEqualByComparingTo("40.00");
    }

    @Test void calculatesCategoryAmountShareAndPopularProducts() {
        var categories = analytics.categoryShare(LocalDate.of(2026,8,1), LocalDate.of(2026,8,4));
        assertThat(categories).extracting(item -> item.categoryName()).containsExactly("数码设备", "办公用品");
        assertThat(categories).extracting(item -> item.percentage()).containsExactly(new BigDecimal("80.00"), new BigDecimal("20.00"));
        var products = analytics.popularProducts(LocalDate.of(2026,8,1), LocalDate.of(2026,8,4), 10);
        assertThat(products.getFirst().productName()).isEqualTo("键盘");
        assertThat(products.getFirst().salesQuantity()).isEqualTo(4);
        assertThat(products.getFirst().orderCount()).isEqualTo(2);
    }

    @Test void zeroFillsDailyWeeklyAndMonthlyBuckets() {
        var daily = analytics.salesTrend(LocalDate.of(2026,8,1), LocalDate.of(2026,8,4), TrendGranularity.DAY);
        assertThat(daily).hasSize(4);
        assertThat(daily).extracting(point -> point.salesQuantity()).containsExactly(3L, 3L, 0L, 0L);
        assertThat(analytics.salesTrend(LocalDate.of(2026,7,1), LocalDate.of(2026,8,31), TrendGranularity.MONTH)).hasSize(2);
        assertThat(analytics.salesTrend(LocalDate.of(2026,8,1), LocalDate.of(2026,8,15), TrendGranularity.WEEK)).hasSize(3);
    }

    @Test void rejectsInvalidParametersBeforeQuerying() {
        assertThatThrownBy(() -> analytics.customerRanking(LocalDate.of(2026,8,2), LocalDate.of(2026,8,1), 10))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("startDate");
        assertThatThrownBy(() -> analytics.popularProducts(LocalDate.of(2026,8,1), LocalDate.of(2026,8,2), 51))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("limit");
    }
}
