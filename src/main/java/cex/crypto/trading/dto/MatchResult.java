package cex.crypto.trading.dto;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.domain.Trade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Result of order matching operation
 * Contains the matched order, generated trades, and modified orders from the book
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MatchResult {
    /**
     * The incoming order with updated filledQuantity and status
     */
    private Order updatedOrder;

    /**
     * List of trades generated during matching
     */
    @Builder.Default
    private List<Trade> trades = new ArrayList<>();

    /**
     * Orders from the order book that were modified (partially or fully filled)
     */
    @Builder.Default
    private List<Order> modifiedOrders = new ArrayList<>();

    /**
     * Whether the incoming order was fully matched
     */
    private boolean fullyMatched;
}
