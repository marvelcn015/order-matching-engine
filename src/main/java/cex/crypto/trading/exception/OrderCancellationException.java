package cex.crypto.trading.exception;

/**
 * Order cancellation exception
 */
public class OrderCancellationException extends BusinessException {
    public OrderCancellationException(String message) {
        super(message);
    }
}
