package com.atwhis.agentdemo.sales.client;
import java.math.BigDecimal;
public record PopularProductItem(Long productId, String productName, long salesQuantity, long orderCount, BigDecimal salesAmount) { }
