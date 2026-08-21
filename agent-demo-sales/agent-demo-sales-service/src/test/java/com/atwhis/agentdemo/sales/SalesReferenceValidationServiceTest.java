package com.atwhis.agentdemo.sales;

import com.atwhis.agentdemo.sales.domain.Customer;
import com.atwhis.agentdemo.sales.domain.Product;
import com.atwhis.agentdemo.sales.domain.ProductCategory;
import com.atwhis.agentdemo.sales.persistence.CustomerMapper;
import com.atwhis.agentdemo.sales.persistence.ProductCategoryMapper;
import com.atwhis.agentdemo.sales.persistence.ProductMapper;
import com.atwhis.agentdemo.sales.persistence.SalesOrderMapper;
import com.atwhis.agentdemo.sales.service.SalesReferenceValidationService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SalesReferenceValidationServiceTest {

    private final CustomerMapper customers = mock(CustomerMapper.class);
    private final ProductCategoryMapper categories = mock(ProductCategoryMapper.class);
    private final ProductMapper products = mock(ProductMapper.class);
    private final SalesOrderMapper orders = mock(SalesOrderMapper.class);
    private final SalesReferenceValidationService service =
            new SalesReferenceValidationService(customers, categories, products, orders);

    @Test
    void acceptsExistingCustomerAndMatchingProductCategory() {
        Product product = new Product();
        product.setCategoryId(3L);
        when(customers.selectById(1L)).thenReturn(new Customer());
        when(categories.selectById(3L)).thenReturn(new ProductCategory());
        when(products.selectById(2L)).thenReturn(product);

        assertThatCode(() -> {
            service.requireCustomer(1L);
            service.requireProductInCategory(2L, 3L);
        }).doesNotThrowAnyException();
    }

    @Test
    void rejectsMissingOrInconsistentReferences() {
        Product product = new Product();
        product.setCategoryId(4L);
        when(categories.selectById(3L)).thenReturn(new ProductCategory());
        when(products.selectById(2L)).thenReturn(product);

        assertThatThrownBy(() -> service.requireCustomer(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Customer not found");
        assertThatThrownBy(() -> service.requireProductInCategory(2L, 3L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does not belong");
        assertThatThrownBy(() -> service.requireOrder(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Sales order not found");
    }
}
