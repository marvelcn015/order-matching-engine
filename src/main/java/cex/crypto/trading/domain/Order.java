package cex.crypto.trading.domain;

import cex.crypto.trading.enums.OrderSide;
import cex.crypto.trading.enums.OrderStatus;
import cex.crypto.trading.enums.OrderType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Order entity representing a trading order
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Order {
    /**
     * Unique order identifier
     */
    private Long orderId;

    /**
     * User who placed the order
     */
    private Long userId;

    /**
     * Trading symbol (e.g., "BTC-USD", "ETH-USD")
     */
    private String symbol;

    /**
     * Order side - BUY or SELL
     */
    private OrderSide side;

    /**
     * Order type - LIMIT or MARKET
     */
    private OrderType type;

    /**
     * Price per unit (null for MARKET orders)
     */
    private BigDecimal price;

    /**
     * Quantity to buy or sell
     */
    private BigDecimal quantity;

    /**
     * Quantity that has been filled (for partial fills)
     */
    @Builder.Default
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    /**
     * Current status of the order
     */
    private OrderStatus status;

    /**
     * Timestamp when order was created
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp when order was last updated
     */
    private LocalDateTime updatedAt;

    /**
     * Get remaining quantity to be filled
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public BigDecimal getRemainingQuantity() {
        return quantity.subtract(filledQuantity);
    }

    /**
     * Check if order is completely filled
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isFilled() {
        return filledQuantity.compareTo(quantity) >= 0;
    }

    /**
     * Check if order is partially filled
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isPartiallyFilled() {
        return filledQuantity.compareTo(BigDecimal.ZERO) > 0
            && filledQuantity.compareTo(quantity) < 0;
    }
}
