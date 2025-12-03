package cex.crypto.trading.service;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.enums.OrderStatus;
import cex.crypto.trading.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

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

    /**
     * Create a new order
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
     * Get order by ID
     *
     * @param orderId the order ID
     * @return the order, or null if not found
     */
    public Order getOrderById(Long orderId) {
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
}
