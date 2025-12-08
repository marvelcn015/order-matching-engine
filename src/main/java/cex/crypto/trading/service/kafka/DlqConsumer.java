package cex.crypto.trading.service.kafka;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.enums.OrderStatus;
import cex.crypto.trading.event.OrderCreatedEvent;
import cex.crypto.trading.service.OrderService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

/**
 * Dead Letter Queue (DLQ) consumer
 * Processes messages that failed after multiple retry attempts
 *
 * Responsibilities:
 * - Mark orders as FAILED in database
 * - Log detailed error information
 * - Store DLQ messages for manual review (future enhancement)
 */
@Slf4j
@Service
public class DlqConsumer {

    @Autowired
    private OrderService orderService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Consume messages from order-input DLQ
     * These are orders that failed processing after 3 retry attempts
     */
    @KafkaListener(
        topics = "${kafka.topics.order-input-dlq}",
        groupId = "dlq-processor-group"
    )
    public void consumeDlqMessage(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {

        log.error("DLQ message received: topic={}, partition={}, offset={}, key={}, value={}",
                record.topic(), record.partition(), record.offset(), record.key(), record.value());

        try {
            // Parse the original OrderCreatedEvent from the DLQ message
            OrderCreatedEvent event = objectMapper.readValue(record.value(), OrderCreatedEvent.class);

            log.error("Processing failed order from DLQ: orderId={}, symbol={}, messageId={}",
                    event.getOrderId(), event.getSymbol(), event.getMessageId());

            // Update order status to FAILED in database
            Order order = orderService.getOrderById(event.getOrderId());
            if (order != null) {
                // Only update if still in PENDING status (avoid overwriting other states)
                if (order.getStatus() == OrderStatus.PENDING) {
                    order.setStatus(OrderStatus.FAILED);
                    orderService.updateOrder(order);
                    log.info("Updated order to FAILED from DLQ: orderId={}, symbol={}",
                            order.getOrderId(), order.getSymbol());
                } else {
                    log.warn("Order in DLQ but not PENDING: orderId={}, currentStatus={}",
                            order.getOrderId(), order.getStatus());
                }
            } else {
                log.error("Order from DLQ not found in database: orderId={}", event.getOrderId());
            }

            // TODO: Store DLQ message in dedicated table for manual review
            // CREATE TABLE dlq_messages (
            //     id BIGINT PRIMARY KEY AUTO_INCREMENT,
            //     topic VARCHAR(255),
            //     partition INT,
            //     offset BIGINT,
            //     key VARCHAR(255),
            //     value TEXT,
            //     order_id BIGINT,
            //     error_reason TEXT,
            //     received_at DATETIME(6),
            //     processed BOOLEAN DEFAULT FALSE
            // );

            // Acknowledge the DLQ message
            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing DLQ message: {}", e.getMessage(), e);

            // Acknowledge anyway to prevent infinite loop
            // DLQ messages should not be retried again
            acknowledgment.acknowledge();
        }
    }

    /**
     * Consume messages from trade-output DLQ (future use)
     * Currently unused but prepared for trade persistence failures
     */
    @KafkaListener(
        topics = "${kafka.topics.trade-output-dlq}",
        groupId = "dlq-processor-group"
    )
    public void consumeTradeDlqMessage(
            ConsumerRecord<String, String> record,
            Acknowledgment acknowledgment) {

        log.error("Trade DLQ message received: topic={}, partition={}, offset={}, value={}",
                record.topic(), record.partition(), record.offset(), record.value());

        try {
            // TODO: Implement trade DLQ processing if needed
            // Currently trades are published after DB persistence,
            // so trade-output-dlq is less critical

            acknowledgment.acknowledge();

        } catch (Exception e) {
            log.error("Error processing trade DLQ message: {}", e.getMessage(), e);
            acknowledgment.acknowledge();
        }
    }
}
