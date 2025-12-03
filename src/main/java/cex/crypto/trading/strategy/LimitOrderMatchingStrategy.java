package cex.crypto.trading.strategy;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.domain.OrderBook;
import cex.crypto.trading.domain.Trade;
import cex.crypto.trading.dto.MatchResult;
import cex.crypto.trading.enums.OrderSide;
import cex.crypto.trading.enums.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Matching strategy for LIMIT orders
 * Implements price-time priority: best price first, then FIFO within price level
 */
@Slf4j
@Component
public class LimitOrderMatchingStrategy implements OrderMatchingStrategy {

    @Override
    public MatchResult match(Order incomingOrder, OrderBook orderBook) {
        log.debug("Matching LIMIT order: {} {} {} @ {} qty={}",
                incomingOrder.getSide(), incomingOrder.getSymbol(),
                incomingOrder.getType(), incomingOrder.getPrice(),
                incomingOrder.getQuantity());

        List<Trade> trades = new ArrayList<>();
        List<Order> modifiedOrders = new ArrayList<>();

        if (incomingOrder.getSide() == OrderSide.BUY) {
            matchBuyOrder(incomingOrder, orderBook, trades, modifiedOrders);
        } else {
            matchSellOrder(incomingOrder, orderBook, trades, modifiedOrders);
        }

        // Update incoming order status
        updateOrderStatus(incomingOrder);

        // Add to order book if not fully filled
        if (!incomingOrder.isFilled()) {
            addToOrderBook(incomingOrder, orderBook);
        }

        boolean fullyMatched = incomingOrder.isFilled();

        log.info("LIMIT order match complete: orderId={}, status={}, filled={}/{}, trades={}",
                incomingOrder.getOrderId(), incomingOrder.getStatus(),
                incomingOrder.getFilledQuantity(), incomingOrder.getQuantity(),
                trades.size());

        return MatchResult.builder()
                .updatedOrder(incomingOrder)
                .trades(trades)
                .modifiedOrders(modifiedOrders)
                .fullyMatched(fullyMatched)
                .build();
    }

    /**
     * Match a BUY limit order against sell orders in the book
     * Traverses sellOrders (ascending - lowest price first)
     * Matches where sellPrice <= buyPrice
     */
    private void matchBuyOrder(Order buyOrder, OrderBook orderBook,
                               List<Trade> trades, List<Order> modifiedOrders) {
        ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> sellOrders =
                orderBook.getSellOrders();

        Iterator<Map.Entry<BigDecimal, ConcurrentLinkedQueue<Order>>> priceIterator =
                sellOrders.entrySet().iterator();

        while (priceIterator.hasNext() &&
               buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {

            Map.Entry<BigDecimal, ConcurrentLinkedQueue<Order>> priceLevel = priceIterator.next();
            BigDecimal sellPrice = priceLevel.getKey();

            // Check price condition: sellPrice must be <= buyPrice
            if (sellPrice.compareTo(buyOrder.getPrice()) > 0) {
                log.debug("No more matches: sellPrice {} > buyPrice {}",
                        sellPrice, buyOrder.getPrice());
                break;
            }

            ConcurrentLinkedQueue<Order> ordersAtPrice = priceLevel.getValue();
            matchAtPriceLevel(buyOrder, ordersAtPrice, sellPrice, true,
                             trades, modifiedOrders);

            // Remove empty price level
            if (ordersAtPrice.isEmpty()) {
                priceIterator.remove();
                log.debug("Removed empty price level: {}", sellPrice);
            }
        }
    }

    /**
     * Match a SELL limit order against buy orders in the book
     * Traverses buyOrders (descending - highest price first)
     * Matches where buyPrice >= sellPrice
     */
    private void matchSellOrder(Order sellOrder, OrderBook orderBook,
                                List<Trade> trades, List<Order> modifiedOrders) {
        ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> buyOrders =
                orderBook.getBuyOrders();

        Iterator<Map.Entry<BigDecimal, ConcurrentLinkedQueue<Order>>> priceIterator =
                buyOrders.entrySet().iterator();

        while (priceIterator.hasNext() &&
               sellOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {

            Map.Entry<BigDecimal, ConcurrentLinkedQueue<Order>> priceLevel = priceIterator.next();
            BigDecimal buyPrice = priceLevel.getKey();

            // Check price condition: buyPrice must be >= sellPrice
            if (buyPrice.compareTo(sellOrder.getPrice()) < 0) {
                log.debug("No more matches: buyPrice {} < sellPrice {}",
                        buyPrice, sellOrder.getPrice());
                break;
            }

            ConcurrentLinkedQueue<Order> ordersAtPrice = priceLevel.getValue();
            matchAtPriceLevel(sellOrder, ordersAtPrice, buyPrice, false,
                             trades, modifiedOrders);

            // Remove empty price level
            if (ordersAtPrice.isEmpty()) {
                priceIterator.remove();
                log.debug("Removed empty price level: {}", buyPrice);
            }
        }
    }

    /**
     * Match orders at a specific price level (FIFO within the level)
     *
     * @param incomingOrder The incoming order to match
     * @param ordersAtPrice Queue of orders at this price level
     * @param matchPrice The price at which to execute (maker price)
     * @param incomingIsBuy Whether the incoming order is a buy order
     * @param trades List to collect generated trades
     * @param modifiedOrders List to collect modified orders from book
     */
    private void matchAtPriceLevel(Order incomingOrder,
                                   ConcurrentLinkedQueue<Order> ordersAtPrice,
                                   BigDecimal matchPrice,
                                   boolean incomingIsBuy,
                                   List<Trade> trades,
                                   List<Order> modifiedOrders) {
        Iterator<Order> orderIterator = ordersAtPrice.iterator();

        while (orderIterator.hasNext() &&
               incomingOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {

            Order bookOrder = orderIterator.next();

            // Calculate match quantity
            BigDecimal matchQty = incomingOrder.getRemainingQuantity()
                    .min(bookOrder.getRemainingQuantity());

            log.debug("Matching {} with {} at price {}: qty={}",
                    incomingOrder.getOrderId(), bookOrder.getOrderId(),
                    matchPrice, matchQty);

            // Create trade (always use maker price)
            Trade trade = Trade.builder()
                    .buyOrderId(incomingIsBuy ? incomingOrder.getOrderId() : bookOrder.getOrderId())
                    .sellOrderId(incomingIsBuy ? bookOrder.getOrderId() : incomingOrder.getOrderId())
                    .symbol(incomingOrder.getSymbol())
                    .price(matchPrice)
                    .quantity(matchQty)
                    .createdAt(LocalDateTime.now())
                    .build();
            trades.add(trade);

            // Update filled quantities
            incomingOrder.setFilledQuantity(
                    incomingOrder.getFilledQuantity().add(matchQty));
            bookOrder.setFilledQuantity(
                    bookOrder.getFilledQuantity().add(matchQty));

            // Update book order status and handle removal
            if (bookOrder.isFilled()) {
                bookOrder.setStatus(OrderStatus.FILLED);
                orderIterator.remove();
                log.debug("Book order {} fully filled and removed", bookOrder.getOrderId());
            } else {
                bookOrder.setStatus(OrderStatus.PARTIALLY_FILLED);
                log.debug("Book order {} partially filled: {}/{}",
                        bookOrder.getOrderId(),
                        bookOrder.getFilledQuantity(),
                        bookOrder.getQuantity());
            }

            bookOrder.setUpdatedAt(LocalDateTime.now());
            modifiedOrders.add(bookOrder);
        }
    }

    /**
     * Update incoming order status based on fill level
     */
    private void updateOrderStatus(Order order) {
        if (order.isFilled()) {
            order.setStatus(OrderStatus.FILLED);
        } else if (order.isPartiallyFilled()) {
            order.setStatus(OrderStatus.PARTIALLY_FILLED);
        } else {
            order.setStatus(OrderStatus.OPEN);
        }
        order.setUpdatedAt(LocalDateTime.now());
    }

    /**
     * Add order to the order book at its price level
     */
    private void addToOrderBook(Order order, OrderBook orderBook) {
        ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> targetMap =
                (order.getSide() == OrderSide.BUY)
                        ? orderBook.getBuyOrders()
                        : orderBook.getSellOrders();

        targetMap.computeIfAbsent(order.getPrice(), k -> new ConcurrentLinkedQueue<>())
                .offer(order);

        log.debug("Added order {} to book at price {}: remaining qty={}",
                order.getOrderId(), order.getPrice(), order.getRemainingQuantity());
    }
}
