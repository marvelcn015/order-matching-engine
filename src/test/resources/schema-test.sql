-- Trading Database Schema for H2 Testing
-- H2-compatible schema (MySQL MODE)

DROP TABLE IF EXISTS trades;
DROP TABLE IF EXISTS order_books;
DROP TABLE IF EXISTS orders;
DROP TABLE IF EXISTS user_balances;
DROP TABLE IF EXISTS users;

-- Users Table
CREATE TABLE users (
    user_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    username VARCHAR(50) NOT NULL UNIQUE,
    email VARCHAR(100) NOT NULL UNIQUE,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_username ON users(username);
CREATE INDEX idx_email ON users(email);

-- User Balances Table
CREATE TABLE user_balances (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    currency VARCHAR(10) NOT NULL,
    available_balance DECIMAL(20, 8) NOT NULL DEFAULT 0,
    frozen_balance DECIMAL(20, 8) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT uk_user_currency UNIQUE (user_id, currency),
    CONSTRAINT fk_user_balance FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX idx_user_id_balance ON user_balances(user_id);

-- Orders Table
CREATE TABLE orders (
    order_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    side VARCHAR(10) NOT NULL,
    type VARCHAR(10) NOT NULL,
    price DECIMAL(20, 8),
    quantity DECIMAL(20, 8) NOT NULL,
    filled_quantity DECIMAL(20, 8) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_user_id ON orders(user_id);
CREATE INDEX idx_symbol_status ON orders(symbol, status);
CREATE INDEX idx_created_at ON orders(created_at);

-- Trades Table
CREATE TABLE trades (
    trade_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    buy_order_id BIGINT NOT NULL,
    sell_order_id BIGINT NOT NULL,
    symbol VARCHAR(20) NOT NULL,
    price DECIMAL(20, 8) NOT NULL,
    quantity DECIMAL(20, 8) NOT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),

    CONSTRAINT fk_buy_order FOREIGN KEY (buy_order_id) REFERENCES orders(order_id),
    CONSTRAINT fk_sell_order FOREIGN KEY (sell_order_id) REFERENCES orders(order_id)
);

CREATE INDEX idx_buy_order ON trades(buy_order_id);
CREATE INDEX idx_sell_order ON trades(sell_order_id);
CREATE INDEX idx_symbol_created ON trades(symbol, created_at);

-- Order Books Table
CREATE TABLE order_books (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    symbol VARCHAR(20) NOT NULL UNIQUE,
    buy_orders CLOB NOT NULL,
    sell_orders CLOB NOT NULL,
    version INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
);

CREATE INDEX idx_symbol ON order_books(symbol);
