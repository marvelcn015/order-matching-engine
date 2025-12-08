package cex.crypto.trading.service.kafka;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.event.OrderCreatedEvent;
import cex.crypto.trading.service.IdempotencyService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Producer service for OrderCreatedEvent
 * Publishes new orders to order-input topic for async processing
 */
@Slf4j
@Service
public class OrderEventProducerService {

    @Autowired
    private KafkaTemplate<String, OrderCreatedEvent> orderKafkaTemplate;

    @Autowired
    private IdempotencyService idempotencyService;

    @Value("${kafka.topics.order-input}")
    private String orderInputTopic;

    /**
     * Publish order to Kafka for async matching
     * Partition key = symbol (ensures same trading pair goes to same partition)
     *
     * @param order the order to publish
     * @param correlationId correlation ID for request tracing
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, OrderCreatedEvent>> publishOrder(
            Order order, String correlationId) {

        // Create event from order
        OrderCreatedEvent event = OrderCreatedEvent.fromOrder(order, correlationId);

        // Idempotency: Record message sent before publishing
        idempotencyService.recordMessageSent(event.getMessageId(), order.getOrderId());

        // Partition by symbol (ensures ordering per trading pair)
        String partitionKey = order.getSymbol();

        log.info("Publishing order to Kafka: orderId={}, symbol={}, messageId={}, correlationId={}",
                order.getOrderId(), order.getSymbol(), event.getMessageId(), correlationId);

        // Send to Kafka asynchronously
        CompletableFuture<SendResult<String, OrderCreatedEvent>> future =
                orderKafkaTemplate.send(orderInputTopic, partitionKey, event);

        // Add callback handlers
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                // Success
                log.info("Order published successfully: orderId={}, partition={}, offset={}",
                        order.getOrderId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                // Failure - remove idempotency record to allow retry
                log.error("Failed to publish order: orderId={}, error={}",
                        order.getOrderId(), ex.getMessage(), ex);
                idempotencyService.removeMessageSent(event.getMessageId());
            }
        });

        return future;
    }
}
