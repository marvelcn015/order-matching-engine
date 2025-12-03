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
 * Matching strategy for MARKET orders
 * Executes immediately at best available prices, crossing multiple price levels if needed
 * Does NOT add unfilled orders to the book (partial fill then cancel)
 */
@Slf4j
@Component
public class MarketOrderMatchingStrategy implements OrderMatchingStrategy {

    @Override
    public MatchResult match(Order incomingOrder, OrderBook orderBook) {
        log.debug("Matching MARKET order: {} {} {} qty={}",
                incomingOrder.getSide(), incomingOrder.getSymbol(),
                incomingOrder.getType(), incomingOrder.getQuantity());

        List<Trade> trades = new ArrayList<>();
        List<Order> modifiedOrders = new ArrayList<>();

        if (incomingOrder.getSide() == OrderSide.BUY) {
            matchBuyOrder(incomingOrder, orderBook, trades, modifiedOrders);
        } else {
            matchSellOrder(incomingOrder, orderBook, trades, modifiedOrders);
        }

        // Update incoming order status
        // Market orders that are not fully filled are marked as PARTIALLY_FILLED
        // (following mainstream exchange behavior: partial fill then cancel)
        updateOrderStatus(incomingOrder);

        // IMPORTANT: Market orders are NEVER added to the order book
        boolean fullyMatched = incomingOrder.isFilled();

        log.info("MARKET order match complete: orderId={}, status={}, filled={}/{}, trades={}",
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
     * Match a BUY market order against sell orders in the book
     * Traverses ALL price levels (ascending - lowest price first) until filled or no liquidity
     * NO price constraint - market order takes all available liquidity
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

            log.debug("MARKET buy crossing price level: {}", sellPrice);

            ConcurrentLinkedQueue<Order> ordersAtPrice = priceLevel.getValue();
            matchAtPriceLevel(buyOrder, ordersAtPrice, sellPrice, true,
                             trades, modifiedOrders);

            // Remove empty price level
            if (ordersAtPrice.isEmpty()) {
                priceIterator.remove();
                log.debug("Removed empty price level: {}", sellPrice);
            }
        }

        // Log if insufficient liquidity
        if (buyOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            log.warn("MARKET buy order {} insufficient liquidity: filled {}/{}, remaining={}",
                    buyOrder.getOrderId(),
                    buyOrder.getFilledQuantity(),
                    buyOrder.getQuantity(),
                    buyOrder.getRemainingQuantity());
        }
    }

    /**
     * Match a SELL market order against buy orders in the book
     * Traverses ALL price levels (descending - highest price first) until filled or no liquidity
     * NO price constraint - market order takes all available liquidity
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

            log.debug("MARKET sell crossing price level: {}", buyPrice);

            ConcurrentLinkedQueue<Order> ordersAtPrice = priceLevel.getValue();
            matchAtPriceLevel(sellOrder, ordersAtPrice, buyPrice, false,
                             trades, modifiedOrders);

            // Remove empty price level
            if (ordersAtPrice.isEmpty()) {
                priceIterator.remove();
                log.debug("Removed empty price level: {}", buyPrice);
            }
        }

        // Log if insufficient liquidity
        if (sellOrder.getRemainingQuantity().compareTo(BigDecimal.ZERO) > 0) {
            log.warn("MARKET sell order {} insufficient liquidity: filled {}/{}, remaining={}",
                    sellOrder.getOrderId(),
                    sellOrder.getFilledQuantity(),
                    sellOrder.getQuantity(),
                    sellOrder.getRemainingQuantity());
        }
    }

    /**
     * Match orders at a specific price level (FIFO within the level)
     *
     * @param incomingOrder The incoming market order to match
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

            log.debug("MARKET matching {} with {} at price {}: qty={}",
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
     * Update incoming market order status based on fill level
     * Market orders are either FILLED or PARTIALLY_FILLED (never OPEN)
     */
    private void updateOrderStatus(Order order) {
        if (order.isFilled()) {
            order.setStatus(OrderStatus.FILLED);
        } else if (order.getFilledQuantity().compareTo(BigDecimal.ZERO) > 0) {
            // Partially filled - following mainstream exchange behavior (partial fill + cancel)
            order.setStatus(OrderStatus.PARTIALLY_FILLED);
        } else {
            // No liquidity at all - reject the order
            order.setStatus(OrderStatus.REJECTED);
            log.warn("MARKET order {} rejected: no liquidity available", order.getOrderId());
        }
        order.setUpdatedAt(LocalDateTime.now());
    }
}
