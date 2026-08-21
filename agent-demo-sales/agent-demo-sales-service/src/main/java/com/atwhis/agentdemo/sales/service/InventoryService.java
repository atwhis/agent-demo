package com.atwhis.agentdemo.sales.service;

import com.atwhis.agentdemo.sales.domain.ProductInventory;
import com.atwhis.agentdemo.sales.persistence.ProductInventoryMapper;
import org.springframework.stereotype.Service;

@Service
public class InventoryService {
    private final ProductInventoryMapper mapper;
    public InventoryService(ProductInventoryMapper mapper) { this.mapper = mapper; }

    public InventoryView getByProductId(long productId) {
        ProductInventory inventory = mapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<ProductInventory>()
                        .eq(ProductInventory::getProductId, productId));
        if (inventory == null) throw new IllegalArgumentException("Inventory not found for product " + productId);
        validate(inventory.getStockQuantity(), inventory.getLockedQuantity());
        return new InventoryView(inventory.getStockQuantity(), inventory.getLockedQuantity(), inventory.availableQuantity());
    }

    public static void validate(int stock, int locked) {
        if (stock < 0 || locked < 0 || locked > stock) {
            throw new IllegalArgumentException("Inventory quantities must satisfy stock >= locked >= 0");
        }
    }

    public record InventoryView(int stockQuantity, int lockedQuantity, int availableQuantity) { }
}
