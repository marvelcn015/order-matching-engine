package cex.crypto.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Trade entity representing a matched trade between two orders
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Trade {
    /**
     * Unique trade identifier
     */
    private Long tradeId;

    /**
     * Buy order ID that was matched
     */
    private Long buyOrderId;

    /**
     * Sell order ID that was matched
     */
    private Long sellOrderId;

    /**
     * Trading symbol (e.g., "BTC-USD", "ETH-USD")
     */
    private String symbol;

    /**
     * Execution price
     */
    private BigDecimal price;

    /**
     * Quantity traded
     */
    private BigDecimal quantity;

    /**
     * Timestamp when trade was executed
     */
    private LocalDateTime createdAt;

    /**
     * Calculate total trade value
     */
    public BigDecimal getTotalValue() {
        return price.multiply(quantity);
    }
}
