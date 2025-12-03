package cex.crypto.trading.service;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.domain.OrderBook;
import cex.crypto.trading.dto.OrderBookDepthResponse;
import cex.crypto.trading.enums.OrderSide;
import cex.crypto.trading.exception.OrderBookNotFoundException;
import cex.crypto.trading.mapper.OrderBookMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    /**
     * Get order book aggregated depth
     *
     * @param symbol the trading symbol
     * @param limit the number of price levels to return (optional)
     * @return order book depth response
     * @throws OrderBookNotFoundException if order book not found
     */
    public OrderBookDepthResponse getOrderBookDepth(String symbol, Integer limit) {
        OrderBook orderBook = getOrderBookBySymbol(symbol);
        if (orderBook == null) {
            throw new OrderBookNotFoundException("Order book not found: " + symbol);
        }

        // Aggregate buy orders depth
        List<OrderBookDepthResponse.PriceLevel> bids = aggregatePriceLevels(
            orderBook.getBuyOrders(), limit);

        // Aggregate sell orders depth
        List<OrderBookDepthResponse.PriceLevel> asks = aggregatePriceLevels(
            orderBook.getSellOrders(), limit);

        return OrderBookDepthResponse.builder()
            .symbol(symbol)
            .bids(bids)
            .asks(asks)
            .bestBid(orderBook.getBestBid())
            .bestAsk(orderBook.getBestAsk())
            .spread(orderBook.getSpread())
            .timestamp(LocalDateTime.now())
            .build();
    }

    /**
     * Aggregate price levels from order map
     *
     * @param orders the orders map (price -> queue of orders)
     * @param limit the number of price levels to return (null for all)
     * @return list of aggregated price levels
     */
    private List<OrderBookDepthResponse.PriceLevel> aggregatePriceLevels(
            ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> orders,
            Integer limit) {

        List<OrderBookDepthResponse.PriceLevel> levels = new ArrayList<>();
        int count = 0;

        for (Map.Entry<BigDecimal, ConcurrentLinkedQueue<Order>> entry : orders.entrySet()) {
            if (limit != null && count >= limit) {
                break;
            }

            BigDecimal price = entry.getKey();
            ConcurrentLinkedQueue<Order> ordersAtPrice = entry.getValue();

            // Calculate total quantity and order count at this price
            BigDecimal totalQuantity = ordersAtPrice.stream()
                .map(Order::getRemainingQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

            levels.add(OrderBookDepthResponse.PriceLevel.builder()
                .price(price)
                .quantity(totalQuantity)
                .orderCount(ordersAtPrice.size())
                .build());

            count++;
        }

        return levels;
    }
}
