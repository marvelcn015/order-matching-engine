package cex.crypto.trading.exception;

/**
 * Order book not found exception
 */
public class OrderBookNotFoundException extends BusinessException {
    public OrderBookNotFoundException(String message) {
        super(message);
    }
}
