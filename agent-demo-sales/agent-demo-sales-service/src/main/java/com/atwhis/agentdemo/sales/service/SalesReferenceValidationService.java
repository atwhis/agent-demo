package com.atwhis.agentdemo.sales.service;

import com.atwhis.agentdemo.sales.domain.Product;
import com.atwhis.agentdemo.sales.persistence.CustomerMapper;
import com.atwhis.agentdemo.sales.persistence.ProductCategoryMapper;
import com.atwhis.agentdemo.sales.persistence.ProductMapper;
import com.atwhis.agentdemo.sales.persistence.SalesOrderMapper;
import org.springframework.stereotype.Service;

/**
 * Validates logical table relationships before a write operation.
 *
 * <p>The demo deliberately keeps foreign keys out of the database schema so that
 * service boundaries can evolve independently. Write use cases should call this
 * service before persisting identifiers received from outside the sales service.</p>
 */
@Service
public class SalesReferenceValidationService {

    private final CustomerMapper customerMapper;
    private final ProductCategoryMapper categoryMapper;
    private final ProductMapper productMapper;
    private final SalesOrderMapper orderMapper;

    public SalesReferenceValidationService(CustomerMapper customerMapper,
                                           ProductCategoryMapper categoryMapper,
                                           ProductMapper productMapper,
                                           SalesOrderMapper orderMapper) {
        this.customerMapper = customerMapper;
        this.categoryMapper = categoryMapper;
        this.productMapper = productMapper;
        this.orderMapper = orderMapper;
    }

    public void requireCustomer(long customerId) {
        requireExists(customerMapper.selectById(customerId), "Customer", customerId);
    }

    public void requireProductInCategory(long productId, long categoryId) {
        requireExists(categoryMapper.selectById(categoryId), "Product category", categoryId);
        Product product = productMapper.selectById(productId);
        requireExists(product, "Product", productId);
        if (!Long.valueOf(categoryId).equals(product.getCategoryId())) {
            throw new IllegalArgumentException("Product does not belong to category " + categoryId);
        }
    }

    public void requireOrder(long orderId) {
        requireExists(orderMapper.selectById(orderId), "Sales order", orderId);
    }

    private static void requireExists(Object value, String type, long id) {
        if (value == null) {
            throw new IllegalArgumentException(type + " not found: " + id);
        }
    }
}
