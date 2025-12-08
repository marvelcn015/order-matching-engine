package cex.crypto.trading.event;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.enums.OrderSide;
import cex.crypto.trading.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Event published when a new order is created
 * Published to order-input topic for async processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique message ID for idempotency (UUID)
     */
    private String messageId;

    /**
     * Event timestamp in epoch milliseconds
     */
    private Long timestamp;

    /**
     * Correlation ID for request tracing
     */
    private String correlationId;

    /**
     * Order ID
     */
    private Long orderId;

    /**
     * User ID who created the order
     */
    private Long userId;

    /**
     * Trading symbol (e.g., BTC-USD)
     */
    private String symbol;

    /**
     * Order side (BUY/SELL)
     */
    private OrderSide side;

    /**
     * Order type (LIMIT/MARKET)
     */
    private OrderType type;

    /**
     * Order price (null for MARKET orders)
     */
    private BigDecimal price;

    /**
     * Order quantity
     */
    private BigDecimal quantity;

    /**
     * Create event from Order entity
     *
     * @param order the order entity
     * @param correlationId correlation ID for tracing
     * @return OrderCreatedEvent
     */
    public static OrderCreatedEvent fromOrder(Order order, String correlationId) {
        return OrderCreatedEvent.builder()
                .messageId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .correlationId(correlationId)
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .symbol(order.getSymbol())
                .side(order.getSide())
                .type(order.getType())
                .price(order.getPrice())
                .quantity(order.getQuantity())
                .build();
    }

    /**
     * Convert event to Order entity
     * Note: This creates a minimal Order without status/timestamps
     *
     * @return Order entity
     */
    public Order toOrder() {
        return Order.builder()
                .orderId(this.orderId)
                .userId(this.userId)
                .symbol(this.symbol)
                .side(this.side)
                .type(this.type)
                .price(this.price)
                .quantity(this.quantity)
                .build();
    }
}
