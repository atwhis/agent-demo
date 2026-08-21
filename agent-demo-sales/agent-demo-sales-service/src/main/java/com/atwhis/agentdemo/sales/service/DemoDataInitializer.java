package com.atwhis.agentdemo.sales.service;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.*;
import java.util.*;

@Component
@ConditionalOnProperty(name = "agent-demo.demo-data.enabled", havingValue = "true")
public class DemoDataInitializer implements ApplicationRunner {
    private static final int CUSTOMER_COUNT = 50;
    private static final int CATEGORY_COUNT = 9;
    private static final int PRODUCT_COUNT = 90;
    private static final int ORDER_COUNT = 1_000;

    private final JdbcTemplate jdbc;
    private final OrderCalculationService calculator;

    public DemoDataInitializer(JdbcTemplate jdbc, OrderCalculationService calculator) {
        this.jdbc = jdbc;
        this.calculator = calculator;
    }

    @Override
    public void run(ApplicationArguments args) { initialize(LocalDate.now(ZoneId.of("Asia/Shanghai"))); }

    @Transactional
    public void initialize(LocalDate today) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM t_customer", Long.class);
        if (count != null && count > 0) return;
        Random random = new Random(20260820L);
        insertCustomers();
        insertCategories();
        List<ProductSeed> products = insertProductsAndInventory();
        List<Long> customerIds = jdbc.queryForList("SELECT id FROM t_customer ORDER BY id", Long.class);
        for (int orderIndex = 1; orderIndex <= ORDER_COUNT; orderIndex++) {
            Long customerId = customerIds.get(weightedIndex(random, customerIds.size(), 2.2));
            LocalDate orderDate = randomDate(random, today, orderIndex);
            LocalDateTime orderTime = orderDate.atTime(9 + random.nextInt(11), random.nextInt(60), random.nextInt(60));
            String status = status(random.nextInt(100));
            int lineCount = 1 + random.nextInt(3);
            List<LineSeed> lines = new ArrayList<>();
            Set<Long> selected = new HashSet<>();
            while (lines.size() < lineCount) {
                ProductSeed product = products.get(weightedIndex(random, products.size(), 1.8));
                if (selected.add(product.id())) {
                    int quantity = 1 + random.nextInt(product.id() <= 12 ? 6 : 3);
                    lines.add(new LineSeed(product, quantity));
                }
            }
            List<OrderCalculationService.OrderLine> calculationLines = lines.stream()
                    .map(line -> new OrderCalculationService.OrderLine(line.product().price(), line.quantity())).toList();
            OrderCalculationService.OrderTotals totals = calculator.calculate(calculationLines);
            long orderId = insertOrder("SO%06d".formatted(orderIndex), customerId, orderTime, totals, status);
            for (LineSeed line : lines) insertItem(orderId, line);
        }
    }

    private void insertCustomers() {
        for (int i = 1; i <= CUSTOMER_COUNT; i++) {
            jdbc.update("INSERT INTO t_customer(customer_no, customer_name, department, status) VALUES (?, ?, ?, 'ENABLED')",
                    "C%04d".formatted(i), "客户%02d".formatted(i), "部门%02d".formatted((i - 1) % 8 + 1));
        }
    }

    private void insertCategories() {
        String[] names = {"办公用品", "数码设备", "电脑配件", "家居用品", "食品饮料", "运动户外", "清洁用品", "员工福利", "工业耗材"};
        for (int i = 0; i < CATEGORY_COUNT; i++) {
            jdbc.update("INSERT INTO t_product_category(category_code, category_name, parent_id, status) VALUES (?, ?, NULL, 'ENABLED')",
                    "CAT%02d".formatted(i + 1), names[i]);
        }
    }

    private List<ProductSeed> insertProductsAndInventory() {
        List<Long> categoryIds = jdbc.queryForList("SELECT id FROM t_product_category ORDER BY id", Long.class);
        List<ProductSeed> products = new ArrayList<>();
        for (int i = 1; i <= PRODUCT_COUNT; i++) {
            int productIndex = i;
            Long categoryId = categoryIds.get((i - 1) % categoryIds.size());
            BigDecimal price = BigDecimal.valueOf(8 + (i * 37L) % 900).setScale(2);
            String productNo = "P%04d".formatted(productIndex);
            String productName = "演示商品%03d".formatted(productIndex);
            KeyHolder key = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO t_product(product_no, product_name, category_id, unit, sale_price, status) VALUES (?, ?, ?, '件', ?, 'ENABLED')",
                        new String[]{"id"});
                ps.setString(1, productNo);
                ps.setString(2, productName);
                ps.setLong(3, categoryId);
                ps.setBigDecimal(4, price);
                return ps;
            }, key);
            long id = Objects.requireNonNull(key.getKey()).longValue();
            jdbc.update("INSERT INTO t_product_inventory(product_id, stock_quantity, locked_quantity, version) VALUES (?, ?, ?, 0)",
                    id, 500 + i * 10, i % 7);
            products.add(new ProductSeed(id, productName, categoryId, price));
        }
        return products;
    }

    private long insertOrder(String no, long customerId, LocalDateTime time,
                             OrderCalculationService.OrderTotals totals, String status) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO t_sales_order(order_no, customer_id, order_time, total_quantity, total_amount, status) VALUES (?, ?, ?, ?, ?, ?)",
                    new String[]{"id"});
            ps.setString(1, no); ps.setLong(2, customerId); ps.setObject(3, time);
            ps.setInt(4, totals.totalQuantity()); ps.setBigDecimal(5, totals.totalAmount()); ps.setString(6, status);
            return ps;
        }, key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private void insertItem(long orderId, LineSeed line) {
        BigDecimal amount = calculator.lineAmount(line.product().price(), line.quantity());
        jdbc.update("""
                INSERT INTO t_sales_order_item(order_id, product_id, product_name, category_id, unit_price, quantity, line_amount)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, orderId, line.product().id(), line.product().name(), line.product().categoryId(),
                line.product().price(), line.quantity(), amount);
    }

    private static int weightedIndex(Random random, int size, double power) {
        return Math.min(size - 1, (int) (Math.pow(random.nextDouble(), power) * size));
    }
    private static LocalDate randomDate(Random random, LocalDate today, int orderIndex) {
        int daysAgo = orderIndex <= 300 ? random.nextInt(20) : random.nextInt(90);
        LocalDate date = today.minusDays(daysAgo);
        if (date.getDayOfWeek() == DayOfWeek.SATURDAY || date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            if (random.nextDouble() < 0.6) date = date.minusDays(date.getDayOfWeek() == DayOfWeek.SATURDAY ? 1 : 2);
        }
        return date;
    }
    private static String status(int value) {
        if (value < 80) return "COMPLETED";
        if (value < 90) return "PAID";
        if (value < 95) return "CREATED";
        return "CANCELLED";
    }
    private record ProductSeed(long id, String name, long categoryId, BigDecimal price) { }
    private record LineSeed(ProductSeed product, int quantity) { }
}
