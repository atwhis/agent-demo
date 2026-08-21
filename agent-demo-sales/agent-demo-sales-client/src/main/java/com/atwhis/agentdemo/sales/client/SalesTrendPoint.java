package com.atwhis.agentdemo.sales.client;
import java.math.BigDecimal;
import java.time.LocalDate;
public record SalesTrendPoint(LocalDate bucketStart, String bucketLabel, long orderCount, long salesQuantity, BigDecimal salesAmount) { }
