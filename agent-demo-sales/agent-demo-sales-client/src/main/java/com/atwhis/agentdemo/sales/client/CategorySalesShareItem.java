package com.atwhis.agentdemo.sales.client;
import java.math.BigDecimal;
public record CategorySalesShareItem(Long categoryId, String categoryName, long salesQuantity, BigDecimal salesAmount, BigDecimal percentage) { }
