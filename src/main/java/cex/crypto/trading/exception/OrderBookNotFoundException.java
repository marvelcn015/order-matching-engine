package cex.crypto.trading.exception;

/**
 * 訂單簿不存在異常
 */
public class OrderBookNotFoundException extends BusinessException {
    public OrderBookNotFoundException(String message) {
        super(message);
    }
}
