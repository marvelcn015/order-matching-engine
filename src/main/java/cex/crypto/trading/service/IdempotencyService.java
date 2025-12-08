package cex.crypto.trading.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

/**
 * Idempotency Service using Redis
 * Prevents duplicate message processing in distributed Kafka consumers
 *
 * Two-level idempotency:
 * 1. Producer-side: Track messages sent to Kafka (prevent duplicate publishes)
 * 2. Consumer-side: Track messages processed from Kafka (prevent duplicate processing)
 */
@Slf4j
@Service
public class IdempotencyService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    /**
     * Redis key prefix for messages sent to Kafka (producer-side)
     */
    private static final String MESSAGE_SENT_PREFIX = "idempotency:sent:";

    /**
     * Redis key prefix for messages processed from Kafka (consumer-side)
     */
    private static final String MESSAGE_PROCESSED_PREFIX = "idempotency:processed:";

    /**
     * TTL for idempotency records (24 hours)
     * Long enough to cover typical Kafka retention and replay scenarios
     */
    private static final long TTL_HOURS = 24;

    /**
     * Record that a message was sent to Kafka (producer-side idempotency)
     * Called before publishing to Kafka
     *
     * @param messageId unique message identifier (UUID)
     * @param orderId the order ID associated with this message
     */
    public void recordMessageSent(String messageId, Long orderId) {
        String key = MESSAGE_SENT_PREFIX + messageId;
        redisTemplate.opsForValue().set(
            key,
            String.valueOf(orderId),
            TTL_HOURS,
            TimeUnit.HOURS
        );
        log.debug("Recorded message sent: messageId={}, orderId={}", messageId, orderId);
    }

    /**
     * Remove message sent record (called on publish failure to allow retry)
     * If Kafka publish fails, we remove the record so the producer can retry
     *
     * @param messageId unique message identifier
     */
    public void removeMessageSent(String messageId) {
        String key = MESSAGE_SENT_PREFIX + messageId;
        redisTemplate.delete(key);
        log.debug("Removed message sent record: messageId={}", messageId);
    }

    /**
     * Check if a message was already processed (consumer-side idempotency)
     * Called at the beginning of consumer processing
     *
     * @param messageId unique message identifier
     * @return true if message was already processed, false otherwise
     */
    public boolean isMessageProcessed(String messageId) {
        String key = MESSAGE_PROCESSED_PREFIX + messageId;
        Boolean exists = redisTemplate.hasKey(key);
        boolean processed = Boolean.TRUE.equals(exists);

        if (processed) {
            log.warn("Duplicate message detected (already processed): messageId={}", messageId);
        }

        return processed;
    }

    /**
     * Mark a message as processed (consumer-side idempotency)
     * Called after successful message processing
     *
     * @param messageId unique message identifier
     * @param orderId the order ID associated with this message
     */
    public void markMessageProcessed(String messageId, Long orderId) {
        String key = MESSAGE_PROCESSED_PREFIX + messageId;
        redisTemplate.opsForValue().set(
            key,
            String.valueOf(orderId),
            TTL_HOURS,
            TimeUnit.HOURS
        );
        log.debug("Marked message as processed: messageId={}, orderId={}", messageId, orderId);
    }

    /**
     * Check if a message was sent (for debugging/monitoring)
     *
     * @param messageId unique message identifier
     * @return true if message was sent, false otherwise
     */
    public boolean isMessageSent(String messageId) {
        String key = MESSAGE_SENT_PREFIX + messageId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    /**
     * Get the order ID associated with a processed message (for debugging)
     *
     * @param messageId unique message identifier
     * @return order ID as string, or null if not found
     */
    public String getProcessedOrderId(String messageId) {
        String key = MESSAGE_PROCESSED_PREFIX + messageId;
        return redisTemplate.opsForValue().get(key);
    }

    /**
     * Manually clear idempotency record (for admin/debugging purposes)
     * Use with caution - only for manual intervention
     *
     * @param messageId unique message identifier
     * @return true if record was deleted, false if it didn't exist
     */
    public boolean clearIdempotencyRecord(String messageId) {
        String sentKey = MESSAGE_SENT_PREFIX + messageId;
        String processedKey = MESSAGE_PROCESSED_PREFIX + messageId;

        Boolean sentDeleted = redisTemplate.delete(sentKey);
        Boolean processedDeleted = redisTemplate.delete(processedKey);

        boolean deleted = Boolean.TRUE.equals(sentDeleted) || Boolean.TRUE.equals(processedDeleted);

        if (deleted) {
            log.info("Manually cleared idempotency record: messageId={}", messageId);
        }

        return deleted;
    }
}
