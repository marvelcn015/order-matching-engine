package cex.crypto.trading.event;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.enums.OrderStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Event published when order status changes
 * Published to order-status-update topic for SSE broadcasting
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderStatusEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Order ID
     */
    private Long orderId;

    /**
     * User ID who owns the order
     */
    private Long userId;

    /**
     * Trading symbol (e.g., BTC-USD)
     */
    private String symbol;

    /**
     * Current order status
     */
    private OrderStatus status;

    /**
     * Filled quantity
     */
    private BigDecimal filledQuantity;

    /**
     * Remaining quantity
     */
    private BigDecimal remainingQuantity;

    /**
     * Event timestamp in epoch milliseconds
     */
    private Long timestamp;

    /**
     * Reason for status change (MATCHED, CANCELLED, REJECTED, FAILED)
     */
    private String reason;

    /**
     * Error message (for FAILED status)
     */
    private String errorMessage;

    /**
     * Create event from Order entity
     *
     * @param order the order entity
     * @param reason the reason for status change
     * @return OrderStatusEvent
     */
    public static OrderStatusEvent fromOrder(Order order, String reason) {
        return OrderStatusEvent.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .symbol(order.getSymbol())
                .status(order.getStatus())
                .filledQuantity(order.getFilledQuantity())
                .remainingQuantity(order.getRemainingQuantity())
                .timestamp(System.currentTimeMillis())
                .reason(reason)
                .build();
    }
}
