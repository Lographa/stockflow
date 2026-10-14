
-- =========================================
-- 1. Criar o banco de dados
-- =========================================

CREATE DATABASE IF NOT EXISTS stockflow_db
    CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE stockflow_db;


-- =========================================
-- 2. Tabela de produtos
-- =========================================

CREATE TABLE IF NOT EXISTS products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    sku VARCHAR(50) NOT NULL UNIQUE,
    name VARCHAR(150) NOT NULL,
    description VARCHAR(500),
    price DECIMAL(12, 2) NOT NULL,
    quantity INT NOT NULL DEFAULT 0,
    min_stock INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
        ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT chk_product_price
        CHECK (price >= 0),

    CONSTRAINT chk_product_quantity
        CHECK (quantity >= 0),

    CONSTRAINT chk_product_min_stock
        CHECK (min_stock >= 0)
);


-- =========================================
-- 3. Tabela de movimentações
-- =========================================

CREATE TABLE IF NOT EXISTS stock_movements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    type VARCHAR(10) NOT NULL,
    quantity INT NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_movement_product
        FOREIGN KEY (product_id)
        REFERENCES products(id),

    CONSTRAINT chk_movement_type
        CHECK (type IN ('ENTRY', 'EXIT')),

    CONSTRAINT chk_movement_quantity
        CHECK (quantity > 0),

    INDEX idx_movement_product_date (product_id, created_at)
);


-- =========================================
-- 4. Inserir produtos iniciais
-- =========================================

INSERT INTO products
    (sku, name, description, price, quantity, min_stock)
VALUES
    ('MOUSE-001', 'Mouse Gamer',
     'Mouse USB com 6 botoes', 129.90, 25, 10),

    ('KEYBOARD-001', 'Teclado Mecanico',
     'Teclado mecanico ABNT2', 249.90, 8, 10),

    ('MONITOR-001', 'Monitor 24',
     'Monitor Full HD 24 polegadas', 899.90, 3, 5);


-- =========================================
-- 5. Inserir histórico inicial
-- =========================================

INSERT INTO stock_movements
    (product_id, type, quantity, description)
SELECT
    p.id,
    m.type,
    m.quantity,
    m.description
FROM (
    SELECT 'MOUSE-001' AS sku, 'ENTRY' AS type,
           30 AS quantity, 'Estoque inicial' AS description
    UNION ALL
    SELECT 'MOUSE-001', 'EXIT', 5, 'Separacao de pedidos'
    UNION ALL
    SELECT 'KEYBOARD-001', 'ENTRY', 10, 'Estoque inicial'
    UNION ALL
    SELECT 'KEYBOARD-001', 'EXIT', 2, 'Separacao de pedidos'
    UNION ALL
    SELECT 'MONITOR-001', 'ENTRY', 5, 'Estoque inicial'
    UNION ALL
    SELECT 'MONITOR-001', 'EXIT', 2, 'Separacao de pedidos'
) AS m
JOIN products p ON p.sku = m.sku;


-- =========================================
-- 6. Consultar os dados
-- =========================================

SELECT * FROM products;

SELECT * FROM stock_movements
ORDER BY created_at, id;
