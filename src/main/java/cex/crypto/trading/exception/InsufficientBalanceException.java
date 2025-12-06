package cex.crypto.trading.exception;

/**
 * Exception thrown when a user has insufficient balance
 */
public class InsufficientBalanceException extends RuntimeException {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}
