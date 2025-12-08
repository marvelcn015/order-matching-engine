package cex.crypto.trading.service.kafka;

import cex.crypto.trading.event.OrderStatusEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Producer service for OrderStatusEvent
 * Publishes order status updates to order-status-update topic for SSE broadcasting
 */
@Slf4j
@Service
public class OrderStatusEventProducerService {

    @Autowired
    private KafkaTemplate<String, OrderStatusEvent> statusKafkaTemplate;

    @Value("${kafka.topics.order-status-update}")
    private String orderStatusUpdateTopic;

    /**
     * Publish order status update to Kafka
     * Partition key = userId (ensures same user's updates go to same partition for ordering)
     *
     * @param event the order status event to publish
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, OrderStatusEvent>> publishStatusUpdate(
            OrderStatusEvent event) {

        // Partition by userId (ensures user sees ordered status updates)
        String partitionKey = String.valueOf(event.getUserId());

        log.debug("Publishing order status update to Kafka: orderId={}, userId={}, status={}, reason={}",
                event.getOrderId(), event.getUserId(), event.getStatus(), event.getReason());

        // Send to Kafka asynchronously
        CompletableFuture<SendResult<String, OrderStatusEvent>> future =
                statusKafkaTemplate.send(orderStatusUpdateTopic, partitionKey, event);

        // Add callback handlers
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                // Success
                log.debug("Order status update published successfully: orderId={}, userId={}, partition={}, offset={}",
                        event.getOrderId(), event.getUserId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                // Failure - log warning (status updates are non-critical)
                log.warn("Failed to publish order status update: orderId={}, userId={}, error={}",
                        event.getOrderId(), event.getUserId(), ex.getMessage());
            }
        });

        return future;
    }

    /**
     * Publish status update synchronously (wait for result)
     * Use sparingly - only when you need to ensure status was sent before continuing
     *
     * @param event the order status event to publish
     * @return SendResult
     * @throws Exception if send fails
     */
    public SendResult<String, OrderStatusEvent> publishStatusUpdateSync(
            OrderStatusEvent event) throws Exception {

        String partitionKey = String.valueOf(event.getUserId());

        log.debug("Publishing order status update (sync) to Kafka: orderId={}, userId={}, status={}",
                event.getOrderId(), event.getUserId(), event.getStatus());

        // Send synchronously (blocks until ack received)
        SendResult<String, OrderStatusEvent> result =
                statusKafkaTemplate.send(orderStatusUpdateTopic, partitionKey, event).get();

        log.debug("Order status update published (sync): orderId={}, partition={}, offset={}",
                event.getOrderId(),
                result.getRecordMetadata().partition(),
                result.getRecordMetadata().offset());

        return result;
    }
}
