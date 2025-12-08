package cex.crypto.trading.exception;

/**
 * Exception thrown when publishing to Kafka fails
 * Indicates the order was accepted but could not be queued for processing
 */
public class KafkaPublishException extends RuntimeException {

    public KafkaPublishException(String message) {
        super(message);
    }

    public KafkaPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}
