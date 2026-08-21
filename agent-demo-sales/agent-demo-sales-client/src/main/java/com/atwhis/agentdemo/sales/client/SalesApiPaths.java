package com.atwhis.agentdemo.sales.client;

public final class SalesApiPaths {
    public static final String SERVICE_NAME = "agent-demo-sales-service";
    public static final String BASE = "/internal/v1/statistics";
    public static final String CUSTOMER_RANKING = BASE + "/customer-purchase-ranking";
    public static final String CATEGORY_SHARE = BASE + "/category-sales-share";
    public static final String POPULAR_PRODUCTS = BASE + "/popular-products";
    public static final String SALES_TREND = BASE + "/sales-trend";
    private SalesApiPaths() { }
}
