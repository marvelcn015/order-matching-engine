package cex.crypto.trading.exception;

/**
 * Order not found exception
 */
public class OrderNotFoundException extends BusinessException {
    public OrderNotFoundException(String message) {
        super(message);
    }
}
