package com.atwhis.agentdemo.sales.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
@TableName("t_sales_order_item")
public class SalesOrderItem {
    @TableId(type = IdType.AUTO) private Long id;
    private Long orderId;
    private Long productId;
    private String productName;
    private Long categoryId;
    private BigDecimal unitPrice;
    private Integer quantity;
    private BigDecimal lineAmount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
