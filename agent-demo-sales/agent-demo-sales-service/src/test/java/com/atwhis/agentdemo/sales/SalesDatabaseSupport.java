package com.atwhis.agentdemo.sales;

import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;
import java.time.LocalDateTime;

final class SalesDatabaseSupport {
    private SalesDatabaseSupport() { }
    static void clear(JdbcTemplate jdbc) {
        jdbc.update("DELETE FROM t_sales_order_item"); jdbc.update("DELETE FROM t_sales_order");
        jdbc.update("DELETE FROM t_product_inventory"); jdbc.update("DELETE FROM t_product");
        jdbc.update("DELETE FROM t_product_category"); jdbc.update("DELETE FROM t_customer");
    }
    static void insertAnalyticsFixture(JdbcTemplate jdbc) {
        jdbc.update("INSERT INTO t_customer(id, customer_no, customer_name, department, status) VALUES (1,'C1','Alice','销售部','ENABLED'),(2,'C2','Bob','财务部','ENABLED')");
        jdbc.update("INSERT INTO t_product_category(id, category_code, category_name, status) VALUES (1,'A','办公用品','ENABLED'),(2,'B','数码设备','ENABLED')");
        jdbc.update("INSERT INTO t_product(id, product_no, product_name, category_id, unit, sale_price, status) VALUES (1,'P1','签字笔',1,'支',10,'ENABLED'),(2,'P2','键盘',2,'件',20,'ENABLED')");
        jdbc.update("INSERT INTO t_product_inventory(product_id, stock_quantity, locked_quantity, version) VALUES (1,100,5,0),(2,50,0,0)");
        insertOrder(jdbc, 1, "SO1", 1, LocalDateTime.of(2026,8,1,10,0), 3, "40.00", "PAID");
        insertItem(jdbc, 1, 1, "签字笔", 1, "10.00", 2);
        insertItem(jdbc, 1, 2, "键盘", 2, "20.00", 1);
        insertOrder(jdbc, 2, "SO2", 2, LocalDateTime.of(2026,8,2,11,0), 3, "60.00", "COMPLETED");
        insertItem(jdbc, 2, 2, "键盘", 2, "20.00", 3);
        insertOrder(jdbc, 3, "SO3", 1, LocalDateTime.of(2026,8,3,12,0), 100, "1000.00", "CANCELLED");
        insertItem(jdbc, 3, 1, "签字笔", 1, "10.00", 100);
        insertOrder(jdbc, 4, "SO4", 1, LocalDateTime.of(2026,8,4,12,0), 50, "500.00", "CREATED");
        insertItem(jdbc, 4, 1, "签字笔", 1, "10.00", 50);
    }
    private static void insertOrder(JdbcTemplate jdbc, long id, String no, long customerId, LocalDateTime time, int quantity, String amount, String status) {
        jdbc.update("INSERT INTO t_sales_order(id,order_no,customer_id,order_time,total_quantity,total_amount,status) VALUES (?,?,?,?,?,?,?)",
                id, no, customerId, time, quantity, new BigDecimal(amount), status);
    }
    private static void insertItem(JdbcTemplate jdbc, long orderId, long productId, String name, long categoryId, String price, int quantity) {
        BigDecimal unitPrice = new BigDecimal(price);
        jdbc.update("INSERT INTO t_sales_order_item(order_id,product_id,product_name,category_id,unit_price,quantity,line_amount) VALUES (?,?,?,?,?,?,?)",
                orderId, productId, name, categoryId, unitPrice, quantity, unitPrice.multiply(BigDecimal.valueOf(quantity)));
    }
}
