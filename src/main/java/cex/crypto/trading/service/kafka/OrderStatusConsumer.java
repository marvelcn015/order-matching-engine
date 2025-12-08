package cex.crypto.trading.service.kafka;

import cex.crypto.trading.event.OrderStatusEvent;
import cex.crypto.trading.service.sse.SseEmitterRegistry;
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

        log.debug("Consumed order status event: orderId={}, userId={}, status={}, reason={}",
                event.getOrderId(), event.getUserId(), event.getStatus(), event.getReason());

        try {
            // Broadcast to all SSE connections for this user
            sseEmitterRegistry.sendToUser(event.getUserId(), event);

            // Acknowledge Kafka message
            acknowledgment.acknowledge();

            log.debug("Broadcasted status update via SSE: orderId={}, userId={}, status={}",
                    event.getOrderId(), event.getUserId(), event.getStatus());

        } catch (Exception e) {
            log.error("Error broadcasting status update: orderId={}, userId={}, error={}",
                    event.getOrderId(), event.getUserId(), e.getMessage(), e);

            // Acknowledge anyway - status updates are non-critical
            // We don't want to block the consumer or retry
            acknowledgment.acknowledge();
        }
    }
}
