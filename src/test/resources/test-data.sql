-- Test Data Initialization
-- Pre-create order books for common symbols

INSERT INTO order_books (symbol, buy_orders, sell_orders, version, updated_at)
VALUES ('BTC-USD', '{}', '{}', 0, CURRENT_TIMESTAMP(6));

INSERT INTO order_books (symbol, buy_orders, sell_orders, version, updated_at)
VALUES ('ETH-USD', '{}', '{}', 0, CURRENT_TIMESTAMP(6));
