package cex.crypto.trading.strategy;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.domain.OrderBook;
import cex.crypto.trading.dto.MatchResult;

/**
 * Strategy interface for order matching
 * Different implementations handle LIMIT and MARKET order types
 */
public interface OrderMatchingStrategy {
    /**
     * Execute order matching logic
     *
     * @param incomingOrder The order to match against the order book
     * @param orderBook The current order book for the symbol
     * @return MatchResult containing updated order, trades, and modified orders
     */
    MatchResult match(Order incomingOrder, OrderBook orderBook);
}
