package com.atwhis.agentdemo.sales.service;

import com.atwhis.agentdemo.sales.domain.Customer;
import com.atwhis.agentdemo.sales.domain.Product;
import com.atwhis.agentdemo.sales.persistence.CustomerMapper;
import com.atwhis.agentdemo.sales.persistence.ProductMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SalesCatalogService {
    private final CustomerMapper customerMapper;
    private final ProductMapper productMapper;
    public SalesCatalogService(CustomerMapper customerMapper, ProductMapper productMapper) {
        this.customerMapper = customerMapper;
        this.productMapper = productMapper;
    }
    public List<Customer> customers() { return customerMapper.selectList(null); }
    public List<Product> products() { return productMapper.selectList(null); }
}
