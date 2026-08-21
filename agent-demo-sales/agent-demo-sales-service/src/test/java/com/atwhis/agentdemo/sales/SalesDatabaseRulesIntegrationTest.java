package com.atwhis.agentdemo.sales;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
class SalesDatabaseRulesIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    @BeforeEach void setUp() { SalesDatabaseSupport.clear(jdbc); SalesDatabaseSupport.insertAnalyticsFixture(jdbc); }

    @Test void ddlDoesNotCreateCrossTableOrBusinessRuleConstraints() {
        jdbc.update("INSERT INTO t_product_inventory(product_id,stock_quantity,locked_quantity,version) VALUES (99,1,2,0)");
        jdbc.update("""
                INSERT INTO t_sales_order_item(order_id,product_id,product_name,category_id,unit_price,quantity,line_amount)
                VALUES (999,999,'错误快照',999,10,2,99)
                """);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM t_product_inventory WHERE product_id=99", Integer.class))
                .isOne();
    }

    @Test void historicalItemSnapshotDoesNotChangeWithProduct() {
        jdbc.update("UPDATE t_product SET product_name='新名称', sale_price=999 WHERE id=1");
        assertThat(jdbc.queryForObject("SELECT product_name FROM t_sales_order_item WHERE order_id=1 AND product_id=1", String.class))
                .isEqualTo("签字笔");
        assertThat(jdbc.queryForObject("SELECT unit_price FROM t_sales_order_item WHERE order_id=1 AND product_id=1", java.math.BigDecimal.class))
                .isEqualByComparingTo("10.00");
    }
}
