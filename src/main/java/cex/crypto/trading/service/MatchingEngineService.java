package cex.crypto.trading.service;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.domain.OrderBook;
import cex.crypto.trading.dto.MatchResult;
import cex.crypto.trading.enums.OrderType;
import cex.crypto.trading.service.redis.OrderBookSyncService;
import cex.crypto.trading.strategy.OrderMatchingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * Core matching engine service
 * Orchestrates the order matching process with proper transaction boundaries and synchronization
 */
@Slf4j
@Service
public class MatchingEngineService {

    @Autowired
    private Map<OrderType, OrderMatchingStrategy> strategies;

    @Autowired
    private OrderService orderService;

    @Autowired
    private TradeService tradeService;

    @Autowired
    private OrderBookService orderBookService;

    @Autowired
    private OrderBookSyncService orderBookSyncService;

    /**
     * Process an incoming order through the matching engine
     * This method is the main entry point for order matching
     *
     * @Transactional ensures atomicity:
     * - Order updates
     * - Trade creations
     * - OrderBook updates
     * All succeed or all rollback together
     *
     * @param order the order to process
     * @return MatchResult containing matched order, trades, and modified orders
     */
    @Transactional
    public MatchResult processOrder(Order order) {
        log.info("Processing order through matching engine: orderId={}, symbol={}, side={}, type={}, price={}, qty={}",
                order.getOrderId(), order.getSymbol(), order.getSide(),
                order.getType(), order.getPrice(), order.getQuantity());

        // Register symbol for Redis sync (idempotent)
        orderBookSyncService.registerSymbol(order.getSymbol());

        // 1. Get or create order book for the symbol
        OrderBook orderBook = orderBookService.getOrCreateOrderBook(order.getSymbol());

        log.debug("OrderBook state before match: symbol={}, bestBid={}, bestAsk={}, spread={}",
                orderBook.getSymbol(),
                orderBook.getBestBid(),
                orderBook.getBestAsk(),
                orderBook.getSpread());

        // 2. Execute matching with synchronization for thread safety
        // Synchronize on the orderBook instance to prevent race conditions
        // Different symbols can be matched concurrently
        MatchResult result;
        synchronized (orderBook) {
            result = executeMatch(order, orderBook);
        }

        // 3. Persist all changes atomically within the transaction
        try {
            // Update incoming order
            orderService.updateOrder(result.getUpdatedOrder());

            // Update all matched orders from the book
            result.getModifiedOrders().forEach(orderService::updateOrder);

            // Create all trade records
            result.getTrades().forEach(tradeService::createTrade);

            // Save order book state with optimistic locking
            // Will retry automatically via @Retryable if version conflict occurs
            orderBookService.saveOrderBook(orderBook);

            log.info("Order processing complete: orderId={}, status={}, filled={}/{}, trades={}, fullyMatched={}",
                    result.getUpdatedOrder().getOrderId(),
                    result.getUpdatedOrder().getStatus(),
                    result.getUpdatedOrder().getFilledQuantity(),
                    result.getUpdatedOrder().getQuantity(),
                    result.getTrades().size(),
                    result.isFullyMatched());

        } catch (Exception e) {
            log.error("Error persisting match results for order {}: {}",
                    order.getOrderId(), e.getMessage(), e);
            throw e; // Rollback transaction
        }

        log.debug("OrderBook state after match: symbol={}, bestBid={}, bestAsk={}, spread={}",
                orderBook.getSymbol(),
                orderBook.getBestBid(),
                orderBook.getBestAsk(),
                orderBook.getSpread());

        return result;
    }

    /**
     * Execute the matching logic using the appropriate strategy
     * This method is synchronized at the caller level
     *
     * @param order the order to match
     * @param orderBook the order book
     * @return MatchResult
     */
    private MatchResult executeMatch(Order order, OrderBook orderBook) {
        // Select strategy based on order type
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
     * Get the current order book for a symbol (read-only)
     *
     * @param symbol the trading symbol
     * @return the order book
     */
    public OrderBook getOrderBook(String symbol) {
        return orderBookService.getOrderBookBySymbol(symbol);
    }
}
