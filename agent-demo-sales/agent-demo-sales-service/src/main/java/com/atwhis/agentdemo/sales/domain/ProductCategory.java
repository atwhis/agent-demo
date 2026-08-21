package com.atwhis.agentdemo.sales.domain;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter @Setter
@TableName("t_product_category")
public class ProductCategory {
    @TableId(type = IdType.AUTO) private Long id;
    private String categoryCode;
    private String categoryName;
    private Long parentId;
    private ResourceStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
