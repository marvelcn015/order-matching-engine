-- Trading Database Schema
-- High-Performance Order Matching Engine

-- Drop tables if they exist (for development)
DROP TABLE IF EXISTS trades;
DROP TABLE IF EXISTS order_books;
DROP TABLE IF EXISTS orders;

-- Orders Table
-- Stores all trading orders (buy and sell)
CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Unique order identifier',
    user_id BIGINT NOT NULL COMMENT 'User who placed the order',
    symbol VARCHAR(20) NOT NULL COMMENT 'Trading symbol (e.g., BTC-USD)',
    side VARCHAR(10) NOT NULL COMMENT 'Order side: BUY or SELL',
    type VARCHAR(10) NOT NULL COMMENT 'Order type: LIMIT or MARKET',
    price DECIMAL(20, 8) COMMENT 'Price per unit (NULL for MARKET orders)',
    quantity DECIMAL(20, 8) NOT NULL COMMENT 'Quantity to buy or sell',
    filled_quantity DECIMAL(20, 8) NOT NULL DEFAULT 0 COMMENT 'Quantity that has been filled',
    status VARCHAR(20) NOT NULL COMMENT 'Order status: PENDING, OPEN, PARTIALLY_FILLED, FILLED, CANCELLED, REJECTED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Order creation timestamp',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update timestamp',

    INDEX idx_user_id (user_id),
    INDEX idx_symbol_status (symbol, status),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Trading orders table';

-- Trades Table
-- Stores matched trades between buy and sell orders
CREATE TABLE trades (
    trade_id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Unique trade identifier',
    buy_order_id BIGINT NOT NULL COMMENT 'Buy order ID',
    sell_order_id BIGINT NOT NULL COMMENT 'Sell order ID',
    symbol VARCHAR(20) NOT NULL COMMENT 'Trading symbol (e.g., BTC-USD)',
    price DECIMAL(20, 8) NOT NULL COMMENT 'Execution price',
    quantity DECIMAL(20, 8) NOT NULL COMMENT 'Quantity traded',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT 'Trade execution timestamp',

    INDEX idx_buy_order (buy_order_id),
    INDEX idx_sell_order (sell_order_id),
    INDEX idx_symbol_created (symbol, created_at),

    CONSTRAINT fk_buy_order FOREIGN KEY (buy_order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_sell_order FOREIGN KEY (sell_order_id) REFERENCES orders(order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Trade execution records';

-- Order Books Table
-- Stores the current state of order books for each trading symbol
-- buyOrders and sellOrders are stored as JSON for flexibility
CREATE TABLE order_books (
    id BIGINT PRIMARY KEY AUTO_INCREMENT COMMENT 'Unique order book identifier',
    symbol VARCHAR(20) NOT NULL UNIQUE COMMENT 'Trading symbol (e.g., BTC-USD)',
    buy_orders JSON NOT NULL COMMENT 'Buy orders (price-ordered map)',
    sell_orders JSON NOT NULL COMMENT 'Sell orders (price-ordered map)',
    version INT NOT NULL DEFAULT 0 COMMENT 'Version number for optimistic locking',
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6) COMMENT 'Last update timestamp',

    INDEX idx_symbol (symbol)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Order books for trading symbols';
