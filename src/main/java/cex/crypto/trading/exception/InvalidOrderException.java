package cex.crypto.trading.exception;

/**
 * 無效訂單異常
 */
public class InvalidOrderException extends BusinessException {
    public InvalidOrderException(String message) {
        super(message);
    }
}
