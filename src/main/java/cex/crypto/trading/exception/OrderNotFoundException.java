package cex.crypto.trading.exception;

/**
 * 訂單不存在異常
 */
public class OrderNotFoundException extends BusinessException {
    public OrderNotFoundException(String message) {
        super(message);
    }
}
