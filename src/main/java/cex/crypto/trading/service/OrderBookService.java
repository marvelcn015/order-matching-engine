package cex.crypto.trading.service;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.domain.OrderBook;
import cex.crypto.trading.enums.OrderSide;
import cex.crypto.trading.mapper.OrderBookMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Service for OrderBook entity management
 * Handles order book retrieval, updates, and persistence with optimistic locking
 */
@Slf4j
@Service
public class OrderBookService {

    @Autowired
    private OrderBookMapper orderBookMapper;

    /**
     * Get or create order book for a symbol
     *
     * @param symbol the trading symbol
     * @return the order book
     */
    public OrderBook getOrCreateOrderBook(String symbol) {
        OrderBook orderBook = orderBookMapper.findBySymbol(symbol);

        if (orderBook == null) {
            // Create new order book
            orderBook = OrderBook.builder()
                    .symbol(symbol)
                    .version(0)
                    .updatedAt(LocalDateTime.now())
                    .build();

            orderBookMapper.insert(orderBook);
            log.info("Created new order book for symbol: {}", symbol);
        }

        return orderBook;
    }

    /**
     * Add an order to the order book at its price level
     *
     * @param orderBook the order book
     * @param order the order to add
     */
    public void addOrderToBook(OrderBook orderBook, Order order) {
        ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> targetMap =
                (order.getSide() == OrderSide.BUY)
                        ? orderBook.getBuyOrders()
                        : orderBook.getSellOrders();

        targetMap.computeIfAbsent(order.getPrice(), k -> new ConcurrentLinkedQueue<>())
                .offer(order);

        orderBook.setUpdatedAt(LocalDateTime.now());

        log.debug("Added order {} to {} order book at price {}: qty={}",
                order.getOrderId(),
                order.getSide(),
                order.getPrice(),
                order.getQuantity());
    }

    /**
     * Remove an order from the order book
     *
     * @param orderBook the order book
     * @param order the order to remove
     */
    public void removeOrderFromBook(OrderBook orderBook, Order order) {
        ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> targetMap =
                (order.getSide() == OrderSide.BUY)
                        ? orderBook.getBuyOrders()
                        : orderBook.getSellOrders();

        ConcurrentLinkedQueue<Order> ordersAtPrice = targetMap.get(order.getPrice());
        if (ordersAtPrice != null) {
            ordersAtPrice.remove(order);

            // Remove empty price level
            if (ordersAtPrice.isEmpty()) {
                targetMap.remove(order.getPrice());
                log.debug("Removed empty price level: {}", order.getPrice());
            }
        }

        orderBook.setUpdatedAt(LocalDateTime.now());

        log.debug("Removed order {} from {} order book at price {}",
                order.getOrderId(),
                order.getSide(),
                order.getPrice());
    }

    /**
     * Save order book to database with optimistic locking
     *
     * @param orderBook the order book to save
     */
    @Retryable(maxAttempts = 3)
    public void saveOrderBook(OrderBook orderBook) {
        orderBook.setUpdatedAt(LocalDateTime.now());

        int result = orderBookMapper.update(orderBook);

        if (result > 0) {
            log.debug("Order book saved: symbol={}, version={}",
                    orderBook.getSymbol(), orderBook.getVersion() + 1);
        } else {
            // Version conflict - optimistic locking failed
            log.warn("Optimistic locking conflict for order book: symbol={}, version={}",
                    orderBook.getSymbol(), orderBook.getVersion());
            throw new RuntimeException("Optimistic locking conflict - order book was modified by another transaction");
        }
    }

    /**
     * Get order book by symbol
     *
     * @param symbol the trading symbol
     * @return the order book, or null if not found
     */
    public OrderBook getOrderBookBySymbol(String symbol) {
        return orderBookMapper.findBySymbol(symbol);
    }
}
