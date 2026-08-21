package com.atwhis.agentdemo.sales;

import com.atwhis.agentdemo.sales.client.*;
import com.atwhis.agentdemo.sales.service.SalesAnalyticsService;
import com.atwhis.agentdemo.sales.web.SalesStatisticsController;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class SalesStatisticsContractTest {
    @Test void sharedFeignContractOwnsAllVersionedMappings() {
        assertMapping("queryCustomerPurchaseRanking", SalesApiPaths.CUSTOMER_RANKING);
        assertMapping("queryCategorySalesShare", SalesApiPaths.CATEGORY_SHARE);
        assertMapping("queryPopularProducts", SalesApiPaths.POPULAR_PRODUCTS);
        assertMapping("querySalesTrend", SalesApiPaths.SALES_TREND);
        assertThat(SalesStatisticsClient.class.getAnnotation(org.springframework.cloud.openfeign.FeignClient.class).name())
                .isEqualTo(SalesApiPaths.SERVICE_NAME);
        assertThat(SalesStatisticsClient.class.isAssignableFrom(SalesStatisticsController.class)).isTrue();
    }

    @Test void responseDtoHasStableFeignCompatibleJsonFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(new PopularProductItem(1L, "键盘", 8, 3, new BigDecimal("99.90")));
        assertThat(json).contains("\"productId\":1", "\"productName\":\"键盘\"", "\"salesQuantity\":8", "\"orderCount\":3", "\"salesAmount\":99.90");
    }

    private static void assertMapping(String methodName, String expected) {
        Method method = java.util.Arrays.stream(SalesStatisticsClient.class.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName)).findFirst().orElseThrow();
        assertThat(method.getAnnotation(GetMapping.class).value()).containsExactly(expected);
    }
}
