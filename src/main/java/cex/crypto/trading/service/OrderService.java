package cex.crypto.trading.service;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.domain.OrderBook;
import cex.crypto.trading.dto.CreateOrderRequest;
import cex.crypto.trading.enums.OrderStatus;
import cex.crypto.trading.enums.OrderType;
import cex.crypto.trading.exception.InvalidOrderException;
import cex.crypto.trading.exception.OrderCancellationException;
import cex.crypto.trading.exception.OrderNotFoundException;
import cex.crypto.trading.mapper.OrderMapper;
import cex.crypto.trading.service.cache.BloomFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for Order entity management
 * Handles order lifecycle, persistence, and queries
 */
@Slf4j
@Service
public class OrderService {

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private OrderBookService orderBookService;

    @Autowired
    private BloomFilterService bloomFilterService;

    /**
     * Create a new order (adds to Bloom Filter)
     *
     * @param order the order to create
     * @return the created order with generated ID
     */
    public Order createOrder(Order order) {
        order.setCreatedAt(LocalDateTime.now());
        order.setUpdatedAt(LocalDateTime.now());

        if (order.getFilledQuantity() == null) {
            order.setFilledQuantity(BigDecimal.ZERO);
        }

        if (order.getStatus() == null) {
            order.setStatus(OrderStatus.PENDING);
        }

        int result = orderMapper.insert(order);
        if (result > 0) {
            // Add to Bloom Filter for cache penetration protection
            bloomFilterService.addOrder(order.getOrderId());

            log.info("Order created: orderId={}, userId={}, symbol={}, side={}, type={}, price={}, qty={}",
                    order.getOrderId(), order.getUserId(), order.getSymbol(),
                    order.getSide(), order.getType(), order.getPrice(), order.getQuantity());
            return order;
        } else {
            throw new RuntimeException("Failed to create order");
        }
    }

    /**
     * Update an existing order
     *
     * @param order the order to update
     * @return the updated order
     */
    public Order updateOrder(Order order) {
        order.setUpdatedAt(LocalDateTime.now());

        int result = orderMapper.update(order);
        if (result > 0) {
            log.debug("Order updated: orderId={}, status={}, filled={}/{}",
                    order.getOrderId(), order.getStatus(),
                    order.getFilledQuantity(), order.getQuantity());
            return order;
        } else {
            throw new RuntimeException("Failed to update order: orderId=" + order.getOrderId());
        }
    }

    /**
     * Update order filled quantity
     *
     * @param order the order to update
     * @param additionalFilled additional quantity that was filled
     */
    public void updateFilledQuantity(Order order, BigDecimal additionalFilled) {
        BigDecimal newFilledQuantity = order.getFilledQuantity().add(additionalFilled);
        order.setFilledQuantity(newFilledQuantity);
        order.setUpdatedAt(LocalDateTime.now());

        log.debug("Order filled quantity updated: orderId={}, filled={}/{}",
                order.getOrderId(), newFilledQuantity, order.getQuantity());
    }

    /**
     * Update order status
     *
     * @param order the order to update
     * @param newStatus the new status
     */
    public void updateOrderStatus(Order order, OrderStatus newStatus) {
        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);
        order.setUpdatedAt(LocalDateTime.now());

        log.info("Order status changed: orderId={}, {} -> {}",
                order.getOrderId(), oldStatus, newStatus);
    }

    /**
     * Get order by ID (with Bloom Filter protection)
     *
     * @param orderId the order ID
     * @return the order, or null if not found
     */
    public Order getOrderById(Long orderId) {
        // Bloom Filter check (prevents cache penetration)
        if (!bloomFilterService.mayExistOrder(orderId)) {
            log.debug("Order not found in Bloom Filter: {}", orderId);
            return null;
        }

        return orderMapper.findById(orderId);
    }

    /**
     * Get all orders by user ID
     *
     * @param userId the user ID
     * @return list of orders
     */
    public List<Order> getOrdersByUserId(Long userId) {
        return orderMapper.findByUserId(userId);
    }

    /**
     * Get orders by symbol and status
     *
     * @param symbol the trading symbol
     * @param status the order status
     * @return list of orders
     */
    public List<Order> getOrdersBySymbolAndStatus(String symbol, OrderStatus status) {
        return orderMapper.findBySymbolAndStatus(symbol, status);
    }

    /**
     * Delete order by ID
     *
     * @param orderId the order ID
     * @return true if deleted, false otherwise
     */
    public boolean deleteOrderById(Long orderId) {
        int result = orderMapper.deleteById(orderId);
        if (result > 0) {
            log.info("Order deleted: orderId={}", orderId);
            return true;
        }
        return false;
    }

    /**
     * Cancel an order (remove from order book and update status)
     *
     * @param orderId the order ID to cancel
     * @param userId the user ID (for authorization)
     * @return the cancelled order
     * @throws OrderNotFoundException if order not found
     * @throws OrderCancellationException if order cannot be cancelled
     */
    @Transactional
    public Order cancelOrder(Long orderId, Long userId) {
        // 1. Query the order
        Order order = getOrderById(orderId);
        if (order == null) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }

        // 2. Verify user authorization
        if (!order.getUserId().equals(userId)) {
            throw new OrderCancellationException("Not authorized to cancel this order");
        }

        // 3. Check order status
        if (order.getStatus() == OrderStatus.FILLED) {
            throw new OrderCancellationException("Filled orders cannot be cancelled");
        }
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new OrderCancellationException("Order already cancelled");
        }
        if (order.getStatus() == OrderStatus.REJECTED) {
            throw new OrderCancellationException("Rejected orders cannot be cancelled");
        }

        // 4. If order is in order book, remove it
        if (order.getStatus() == OrderStatus.OPEN ||
            order.getStatus() == OrderStatus.PARTIALLY_FILLED) {
            OrderBook orderBook = orderBookService.getOrderBookBySymbol(order.getSymbol());
            if (orderBook != null) {
                synchronized (orderBook) {
                    orderBookService.removeOrderFromBook(orderBook, order);
                    orderBookService.saveOrderBook(orderBook);
                }
                log.info("Removed order {} from order book", orderId);
            }
        }

        // 5. Update order status to CANCELLED
        order.setStatus(OrderStatus.CANCELLED);
        order.setUpdatedAt(LocalDateTime.now());
        updateOrder(order);

        log.info("Order cancelled: orderId={}, userId={}", orderId, userId);
        return order;
    }

    /**
     * Validate create order request
     *
     * @param request the create order request
     * @throws InvalidOrderException if validation fails
     */
    public void validateCreateOrderRequest(CreateOrderRequest request) {
        // LIMIT orders must have price
        if (request.getType() == OrderType.LIMIT && request.getPrice() == null) {
            throw new InvalidOrderException("LIMIT orders must specify price");
        }

        // MARKET orders should not have price
        if (request.getType() == OrderType.MARKET && request.getPrice() != null) {
            throw new InvalidOrderException("MARKET orders should not specify price");
        }

        // Business rule validation: check if symbol is supported (optional)
        // Business rule validation: check if userId exists (optional)

        log.debug("Order request validated: {}", request);
    }
}
