package com.atwhis.agentdemo.sales.client;
import java.math.BigDecimal;
public record CustomerPurchaseRankingItem(Long customerId, String customerName, long purchaseQuantity, long orderCount, BigDecimal salesAmount) { }
