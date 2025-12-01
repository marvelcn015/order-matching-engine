package cex.crypto.trading.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * OrderBook entity representing the order book for a trading symbol
 * Uses ConcurrentSkipListMap for thread-safe sorted price levels
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderBook {
    /**
     * Unique order book identifier
     */
    private Long id;

    /**
     * Trading symbol (e.g., "BTC-USD", "ETH-USD")
     */
    private String symbol;

    /**
     * Buy orders sorted by price (descending - highest price first)
     * Each price level contains a queue of orders in FIFO order
     */
    private ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> buyOrders;

    /**
     * Sell orders sorted by price (ascending - lowest price first)
     * Each price level contains a queue of orders in FIFO order
     */
    private ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> sellOrders;

    /**
     * Version number for optimistic locking
     */
    private Integer version;

    /**
     * Timestamp when order book was last updated
     */
    private LocalDateTime updatedAt;

    /**
     * Custom builder to initialize the concurrent maps with proper comparators
     */
    public static class OrderBookBuilder {
        private Long id;
        private String symbol;
        private ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> buyOrders;
        private ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> sellOrders;
        private Integer version;
        private LocalDateTime updatedAt;

        public OrderBookBuilder id(Long id) {
            this.id = id;
            return this;
        }

        public OrderBookBuilder symbol(String symbol) {
            this.symbol = symbol;
            return this;
        }

        public OrderBookBuilder buyOrders(ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> buyOrders) {
            this.buyOrders = buyOrders;
            return this;
        }

        public OrderBookBuilder sellOrders(ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> sellOrders) {
            this.sellOrders = sellOrders;
            return this;
        }

        public OrderBookBuilder version(Integer version) {
            this.version = version;
            return this;
        }

        public OrderBookBuilder updatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public OrderBook build() {
            // Initialize buy orders with descending comparator (highest price first)
            if (buyOrders == null) {
                buyOrders = new ConcurrentSkipListMap<>(Comparator.reverseOrder());
            }

            // Initialize sell orders with ascending comparator (lowest price first)
            if (sellOrders == null) {
                sellOrders = new ConcurrentSkipListMap<>();
            }

            // Initialize version if not set
            if (version == null) {
                version = 0;
            }

            return new OrderBook(id, symbol, buyOrders, sellOrders, version, updatedAt);
        }
    }

    public static OrderBookBuilder builder() {
        return new OrderBookBuilder();
    }

    /**
     * Get best bid price (highest buy price)
     */
    public BigDecimal getBestBid() {
        return buyOrders.isEmpty() ? null : buyOrders.firstKey();
    }

    /**
     * Get best ask price (lowest sell price)
     */
    public BigDecimal getBestAsk() {
        return sellOrders.isEmpty() ? null : sellOrders.firstKey();
    }

    /**
     * Get bid-ask spread
     */
    public BigDecimal getSpread() {
        BigDecimal bid = getBestBid();
        BigDecimal ask = getBestAsk();
        if (bid == null || ask == null) {
            return null;
        }
        return ask.subtract(bid);
    }

    /**
     * Get mid price
     */
    public BigDecimal getMidPrice() {
        BigDecimal bid = getBestBid();
        BigDecimal ask = getBestAsk();
        if (bid == null || ask == null) {
            return null;
        }
        return bid.add(ask).divide(BigDecimal.valueOf(2));
    }
}
