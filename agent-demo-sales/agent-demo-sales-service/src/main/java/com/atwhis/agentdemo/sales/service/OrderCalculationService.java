package com.atwhis.agentdemo.sales.service;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class OrderCalculationService {
    public OrderTotals calculate(List<OrderLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("Order must contain at least one line");
        }
        int quantity = 0;
        BigDecimal amount = BigDecimal.ZERO;
        for (OrderLine line : lines) {
            if (line.unitPrice() == null || line.unitPrice().signum() < 0 || line.quantity() <= 0) {
                throw new IllegalArgumentException("Unit price must be non-negative and quantity must be positive");
            }
            quantity = Math.addExact(quantity, line.quantity());
            amount = amount.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
        }
        return new OrderTotals(quantity, amount.setScale(2, RoundingMode.HALF_UP));
    }

    public BigDecimal lineAmount(BigDecimal unitPrice, int quantity) {
        return calculate(List.of(new OrderLine(unitPrice, quantity))).totalAmount();
    }

    public record OrderLine(BigDecimal unitPrice, int quantity) { }
    public record OrderTotals(int totalQuantity, BigDecimal totalAmount) { }
}
