package cex.crypto.trading.exception;

/**
 * 訂單取消異常
 */
public class OrderCancellationException extends BusinessException {
    public OrderCancellationException(String message) {
        super(message);
    }
}
