package cex.crypto.trading.service.kafka;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.domain.OrderBook;
import cex.crypto.trading.dto.MatchResult;
import cex.crypto.trading.enums.OrderStatus;
import cex.crypto.trading.enums.OrderType;
import cex.crypto.trading.event.OrderCreatedEvent;
import cex.crypto.trading.event.OrderStatusEvent;
import cex.crypto.trading.event.TradeExecutedEvent;
import cex.crypto.trading.service.IdempotencyService;
import cex.crypto.trading.service.OrderBookService;
import cex.crypto.trading.service.OrderService;
import cex.crypto.trading.service.TradeService;
import cex.crypto.trading.strategy.OrderMatchingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Kafka consumer for order matching
 * Consumes orders from order-input topic and executes matching logic
 */
@Slf4j
@Service
public class MatchingEngineConsumer {

    @Autowired
    private Map<OrderType, OrderMatchingStrategy> strategies;

    @Autowired
    private OrderService orderService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private OrderBookService orderBookService;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private OrderStatusEventProducerService statusEventProducer;

    @Autowired
    private TradeEventProducerService tradeEventProducer;

    /**
     * Consume order from Kafka and process through matching engine
     *
     * Consumer group: matching-engine-consumer-group
     * Concurrency: 6 threads (configured in KafkaConsumerConfig)
     * Acknowledgment: Manual (after successful processing)
     * Error handling: Retry 3 times, then DLQ
     */
    @KafkaListener(
        topics = "${kafka.topics.order-input}",
        groupId = "matching-engine-consumer-group",
        containerFactory = "orderKafkaListenerContainerFactory"
    )
    public void consumeOrder(
            OrderCreatedEvent event,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            Acknowledgment acknowledgment) {

        log.info("Consumed order event: orderId={}, messageId={}, symbol={}, partition={}, offset={}",
                event.getOrderId(), event.getMessageId(), event.getSymbol(), partition, offset);

        try {
            // 1. Idempotency check - prevent duplicate processing
            if (idempotencyService.isMessageProcessed(event.getMessageId())) {
                log.warn("Duplicate message detected, skipping: messageId={}, orderId={}",
                        event.getMessageId(), event.getOrderId());
                acknowledgment.acknowledge();
                return;
            }

            // 2. Load order from database
            Order order = orderService.getOrderById(event.getOrderId());
            if (order == null) {
                log.error("Order not found in database: orderId={}", event.getOrderId());
                acknowledgment.acknowledge(); // Skip this message
                return;
            }

            // 3. Check order status (only process PENDING orders)
            if (order.getStatus() != OrderStatus.PENDING) {
                log.warn("Order already processed: orderId={}, status={}",
                        order.getOrderId(), order.getStatus());
                acknowledgment.acknowledge();
                return;
            }

            // 4. Process order through matching engine
            processOrder(order);

            // 5. Mark message as processed (idempotency)
            idempotencyService.markMessageProcessed(event.getMessageId(), event.getOrderId());

            // 6. Acknowledge Kafka message (commits offset)
            acknowledgment.acknowledge();

            log.info("Order processing complete: orderId={}, finalStatus={}",
                    event.getOrderId(), order.getStatus());

        } catch (Exception e) {
            log.error("Error processing order: orderId={}, messageId={}, error={}",
                    event.getOrderId(), event.getMessageId(), e.getMessage(), e);

            // Update order to FAILED status
            updateOrderToFailed(event.getOrderId(), e.getMessage());

            // Re-throw exception to trigger retry/DLQ mechanism
            throw new RuntimeException("Order processing failed: " + event.getOrderId(), e);
        }
    }

    /**
     * Process order through matching engine
     * This reuses the existing matching strategy pattern
     */
    private void processOrder(Order order) {
        log.info("Processing order: orderId={}, symbol={}, side={}, type={}, price={}, qty={}",
                order.getOrderId(), order.getSymbol(), order.getSide(),
                order.getType(), order.getPrice(), order.getQuantity());

        // 1. Get or create order book for the symbol
        OrderBook orderBook = orderBookService.getOrCreateOrderBook(order.getSymbol());

        // 2. Execute matching with synchronization (per symbol)
        // Different symbols can be matched concurrently (different partitions)
        MatchResult result;
        synchronized (orderBook) {
            result = executeMatch(order, orderBook);
        }

        // 3. Persist all changes atomically (transactional)
        persistMatchResult(result, orderBook);

        // 4. Publish events (status updates and trades)
        publishMatchEvents(result);
    }

    /**
     * Execute matching using appropriate strategy
     * Called within synchronized block
     */
    private MatchResult executeMatch(Order order, OrderBook orderBook) {
        OrderMatchingStrategy strategy = strategies.get(order.getType());

        if (strategy == null) {
            throw new IllegalArgumentException("No matching strategy found for order type: " + order.getType());
        }

        log.debug("Using strategy: {} for order: {}",
                strategy.getClass().getSimpleName(), order.getOrderId());

        // Execute matching
        return strategy.match(order, orderBook);
    }

    /**
     * Persist match result to database (transactional)
     * All updates succeed or all rollback together
     */
    @Transactional
    protected void persistMatchResult(MatchResult result, OrderBook orderBook) {
        // Update incoming order
        orderService.updateOrder(result.getUpdatedOrder());

        // Update all matched orders from the book
        result.getModifiedOrders().forEach(orderService::updateOrder);

        // Create all trade records
        result.getTrades().forEach(tradeService::createTrade);

        // Save order book state (with optimistic locking)
        orderBookService.saveOrderBook(orderBook);

        log.debug("Persisted match result: updatedOrder={}, modifiedOrders={}, trades={}",
                result.getUpdatedOrder().getOrderId(),
                result.getModifiedOrders().size(),
                result.getTrades().size());
    }

    /**
     * Publish events after matching
     * - Order status updates (for SSE broadcasting)
     * - Trade events (for trade-output topic)
     */
    private void publishMatchEvents(MatchResult result) {
        Order order = result.getUpdatedOrder();

        // Publish order status update for incoming order
        OrderStatusEvent statusEvent = OrderStatusEvent.fromOrder(order, "MATCHED");
        statusEventProducer.publishStatusUpdate(statusEvent);

        // Publish trade events and status updates for maker orders
        result.getTrades().forEach(trade -> {
            // Publish trade event
            TradeExecutedEvent tradeEvent = TradeExecutedEvent.fromTrade(trade, order.getOrderId());
            tradeEventProducer.publishTradeEvent(tradeEvent);

            log.info("Trade executed: tradeId={}, buyOrderId={}, sellOrderId={}, price={}, qty={}",
                    trade.getTradeId(), trade.getBuyOrderId(), trade.getSellOrderId(),
                    trade.getPrice(), trade.getQuantity());
        });

        // Publish status updates for maker orders (from the book)
        result.getModifiedOrders().forEach(makerOrder -> {
            OrderStatusEvent makerStatusEvent = OrderStatusEvent.fromOrder(makerOrder, "MATCHED");
            statusEventProducer.publishStatusUpdate(makerStatusEvent);
        });

        log.debug("Published events: statusUpdates={}, trades={}",
                1 + result.getModifiedOrders().size(),
                result.getTrades().size());
    }

    /**
     * Update order to FAILED status when processing fails
     * Also publishes FAILED status event
     */
    private void updateOrderToFailed(Long orderId, String errorMessage) {
        try {
            Order order = orderService.getOrderById(orderId);
            if (order != null) {
                order.setStatus(OrderStatus.FAILED);
                orderService.updateOrder(order);

                // Publish FAILED status event
                OrderStatusEvent failedEvent = OrderStatusEvent.builder()
                        .orderId(orderId)
                        .userId(order.getUserId())
                        .symbol(order.getSymbol())
                        .status(OrderStatus.FAILED)
                        .filledQuantity(order.getFilledQuantity())
                        .remainingQuantity(order.getRemainingQuantity())
                        .timestamp(System.currentTimeMillis())
                        .reason("PROCESSING_ERROR")
                        .errorMessage(errorMessage)
                        .build();

                statusEventProducer.publishStatusUpdate(failedEvent);

                log.info("Updated order to FAILED: orderId={}, error={}", orderId, errorMessage);
            }
        } catch (Exception ex) {
            log.error("Failed to update order to FAILED status: orderId={}, error={}",
                    orderId, ex.getMessage(), ex);
        }
    }
}
