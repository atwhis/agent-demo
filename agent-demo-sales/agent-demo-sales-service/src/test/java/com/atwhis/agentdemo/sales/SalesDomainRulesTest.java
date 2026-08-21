package com.atwhis.agentdemo.sales;

import com.atwhis.agentdemo.sales.service.InventoryService;
import com.atwhis.agentdemo.sales.service.OrderCalculationService;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class SalesDomainRulesTest {
    private final OrderCalculationService calculator = new OrderCalculationService();

    @Test void derivesLineAndOrderTotals() {
        var totals = calculator.calculate(List.of(
                new OrderCalculationService.OrderLine(new BigDecimal("10.25"), 2),
                new OrderCalculationService.OrderLine(new BigDecimal("3.10"), 3)));
        assertThat(totals.totalQuantity()).isEqualTo(5);
        assertThat(totals.totalAmount()).isEqualByComparingTo("29.80");
    }

    @Test void rejectsInvalidAmountsAndInventory() {
        assertThatThrownBy(() -> calculator.lineAmount(new BigDecimal("-1"), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InventoryService.validate(2, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> InventoryService.validate(10, 3)).doesNotThrowAnyException();
    }
}
