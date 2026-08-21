package com.atwhis.agentdemo.sales.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
@TableName("t_sales_order")
public class SalesOrder {
    @TableId(type = IdType.AUTO) private Long id;
    private String orderNo;
    private Long customerId;
    private LocalDateTime orderTime;
    private Integer totalQuantity;
    private BigDecimal totalAmount;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
