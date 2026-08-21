package com.atwhis.agentdemo.sales.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter @Setter
@TableName("t_product")
public class Product {
    @TableId(type = IdType.AUTO) private Long id;
    private String productNo;
    private String productName;
    private Long categoryId;
    private String unit;
    private BigDecimal salePrice;
    private ResourceStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
