package com.atwhis.agentdemo.sales.client;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.util.List;

@FeignClient(name = SalesApiPaths.SERVICE_NAME, contextId = "salesStatisticsClient")
public interface SalesStatisticsClient {
    @GetMapping(SalesApiPaths.CUSTOMER_RANKING)
    List<CustomerPurchaseRankingItem> queryCustomerPurchaseRanking(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit);

    @GetMapping(SalesApiPaths.CATEGORY_SHARE)
    List<CategorySalesShareItem> queryCategorySalesShare(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate);

    @GetMapping(SalesApiPaths.POPULAR_PRODUCTS)
    List<PopularProductItem> queryPopularProducts(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int limit);

    @GetMapping(SalesApiPaths.SALES_TREND)
    List<SalesTrendPoint> querySalesTrend(
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(defaultValue = "DAY") @NotNull TrendGranularity granularity);
}
