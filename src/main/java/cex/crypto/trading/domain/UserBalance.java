package cex.crypto.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * User balance entity supporting multiple currencies
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBalance {
    /**
     * Unique balance record identifier
     */
    private Long id;

    /**
     * User ID reference
     */
    private Long userId;

    /**
     * Currency code (USD, BTC, ETH, etc.)
     */
    private String currency;

    /**
     * Available balance (can be used for trading)
     */
    @Builder.Default
    private BigDecimal availableBalance = BigDecimal.ZERO;

    /**
     * Frozen balance (locked in active orders)
     */
    @Builder.Default
    private BigDecimal frozenBalance = BigDecimal.ZERO;

    /**
     * Timestamp when balance record was created
     */
    private LocalDateTime createdAt;

    /**
     * Timestamp when balance record was last updated
     */
    private LocalDateTime updatedAt;

    /**
     * Get total balance (available + frozen)
     */
    @com.fasterxml.jackson.annotation.JsonIgnore
    public BigDecimal getTotalBalance() {
        return availableBalance.add(frozenBalance);
    }
}
