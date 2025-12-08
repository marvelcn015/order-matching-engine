package cex.crypto.trading.service.kafka;

import cex.crypto.trading.event.OrderStatusEvent;
import cex.crypto.trading.service.sse.SseEmitterRegistry;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Kafka consumer for order status updates
 * Consumes status updates and broadcasts them to SSE clients
 */
@Slf4j
@Service
public class OrderStatusConsumer {

    @Autowired
    private SseEmitterRegistry sseEmitterRegistry;

    @Autowired
    private MeterRegistry meterRegistry;

    // Metrics counters and timers
    private Counter messagesReceivedCounter;
    private Counter messagesProcessedCounter;
    private Counter messagesBroadcastFailedCounter;
    private Timer broadcastTimer;

    /**
     * Initialize metrics on application startup
     */
    @PostConstruct
    public void initMetrics() {
        String consumerGroup = "order-status-broadcaster-group";
        String topic = "order-status-update";

        messagesReceivedCounter = Counter.builder("kafka.consumer.messages.received")
                .description("Total messages received by consumer")
                .tag("consumer_group", consumerGroup)
                .tag("topic", topic)
                .tag("consumer", "order-status")
                .register(meterRegistry);

        messagesProcessedCounter = Counter.builder("kafka.consumer.messages.processed")
                .description("Total messages successfully processed")
                .tag("consumer_group", consumerGroup)
                .tag("topic", topic)
                .tag("consumer", "order-status")
                .register(meterRegistry);

        messagesBroadcastFailedCounter = Counter.builder("kafka.consumer.messages.failed")
                .description("Total messages failed to broadcast")
                .tag("consumer_group", consumerGroup)
                .tag("topic", topic)
                .tag("consumer", "order-status")
                .register(meterRegistry);

        broadcastTimer = Timer.builder("kafka.consumer.processing.time")
                .description("Time taken to broadcast SSE message")
                .tag("consumer_group", consumerGroup)
                .tag("topic", topic)
                .tag("consumer", "order-status")
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry);

        log.info("Initialized metrics for OrderStatusConsumer");
    }

    /**
     * Consume order status events and broadcast to SSE clients
     *
     * Consumer group: order-status-broadcaster-group
     * Concurrency: 2 threads (configured in KafkaConsumerConfig)
     * Acknowledgment: Manual (after successful processing)
     * Error handling: No retry (status updates are non-critical)
     */
    @KafkaListener(
        topics = "${kafka.topics.order-status-update}",
        groupId = "order-status-broadcaster-group",
        containerFactory = "statusKafkaListenerContainerFactory"
    )
    public void consumeStatusUpdate(
            OrderStatusEvent event,
            Acknowledgment acknowledgment) {

        // Increment received counter
        messagesReceivedCounter.increment();

        log.debug("Consumed order status event: orderId={}, userId={}, status={}, reason={}",
                event.getOrderId(), event.getUserId(), event.getStatus(), event.getReason());

        // Wrap processing in timer
        broadcastTimer.record(() -> {
            try {
                // Broadcast to all SSE connections for this user
                sseEmitterRegistry.sendToUser(event.getUserId(), event);

                // Acknowledge Kafka message
                acknowledgment.acknowledge();

                // Increment success counter
                messagesProcessedCounter.increment();

                log.debug("Broadcasted status update via SSE: orderId={}, userId={}, status={}",
                        event.getOrderId(), event.getUserId(), event.getStatus());

            } catch (Exception e) {
                log.error("Error broadcasting status update: orderId={}, userId={}, error={}",
                        event.getOrderId(), event.getUserId(), e.getMessage(), e);

                // Increment failure counter
                messagesBroadcastFailedCounter.increment();

                // Acknowledge anyway - status updates are non-critical
                // We don't want to block the consumer or retry
                acknowledgment.acknowledge();
            }
        });
    }
}
