package com.atwhis.agentdemo.sales.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
@TableName("t_product_inventory")
public class ProductInventory {
    @TableId(type = IdType.AUTO) private Long id;
    private Long productId;
    private Integer stockQuantity;
    private Integer lockedQuantity;
    private Integer version;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    public int availableQuantity() { return stockQuantity - lockedQuantity; }
}
