CREATE TABLE IF NOT EXISTS t_customer (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_no VARCHAR(32) NOT NULL,
    customer_name VARCHAR(100) NOT NULL,
    department VARCHAR(100),
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE INDEX uk_customer_no (customer_no)
);

CREATE TABLE IF NOT EXISTS t_product_category (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    category_code VARCHAR(32) NOT NULL,
    category_name VARCHAR(100) NOT NULL,
    parent_id BIGINT,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE INDEX uk_category_code (category_code)
);

CREATE TABLE IF NOT EXISTS t_product (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_no VARCHAR(32) NOT NULL,
    product_name VARCHAR(120) NOT NULL,
    category_id BIGINT NOT NULL,
    unit VARCHAR(20) NOT NULL,
    sale_price DECIMAL(18,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE INDEX uk_product_no (product_no),
    INDEX idx_product_category (category_id)
);

CREATE TABLE IF NOT EXISTS t_product_inventory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    stock_quantity INT NOT NULL,
    locked_quantity INT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE INDEX uk_inventory_product_id (product_id)
);

CREATE TABLE IF NOT EXISTS t_sales_order (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_no VARCHAR(40) NOT NULL,
    customer_id BIGINT NOT NULL,
    order_time DATETIME(3) NOT NULL,
    total_quantity INT NOT NULL,
    total_amount DECIMAL(18,2) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    UNIQUE INDEX uk_order_no (order_no),
    INDEX idx_order_time_status (order_time, status),
    INDEX idx_order_customer_time (customer_id, order_time)
);

CREATE TABLE IF NOT EXISTS t_sales_order_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    product_name VARCHAR(120) NOT NULL,
    category_id BIGINT NOT NULL,
    unit_price DECIMAL(18,2) NOT NULL,
    quantity INT NOT NULL,
    line_amount DECIMAL(18,2) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    INDEX idx_item_order (order_id),
    INDEX idx_item_product (product_id),
    INDEX idx_item_category (category_id)
);
