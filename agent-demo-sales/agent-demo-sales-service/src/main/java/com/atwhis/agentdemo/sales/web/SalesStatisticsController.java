package com.atwhis.agentdemo.sales.web;

import com.atwhis.agentdemo.sales.client.*;
import com.atwhis.agentdemo.sales.service.SalesAnalyticsService;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;
import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
public class SalesStatisticsController implements SalesStatisticsClient {
    private final SalesAnalyticsService analytics;
    public SalesStatisticsController(SalesAnalyticsService analytics) { this.analytics = analytics; }
    @Override public List<CustomerPurchaseRankingItem> queryCustomerPurchaseRanking(LocalDate startDate, LocalDate endDate, int limit) {
        return analytics.customerRanking(startDate, endDate, limit);
    }
    @Override public List<CategorySalesShareItem> queryCategorySalesShare(LocalDate startDate, LocalDate endDate) {
        return analytics.categoryShare(startDate, endDate);
    }
    @Override public List<PopularProductItem> queryPopularProducts(LocalDate startDate, LocalDate endDate, int limit) {
        return analytics.popularProducts(startDate, endDate, limit);
    }
    @Override public List<SalesTrendPoint> querySalesTrend(LocalDate startDate, LocalDate endDate, TrendGranularity granularity) {
        return analytics.salesTrend(startDate, endDate, granularity);
    }
}
