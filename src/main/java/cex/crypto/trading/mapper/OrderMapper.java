package cex.crypto.trading.mapper;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.enums.OrderStatus;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper interface for Order entity
 */
@Mapper
public interface OrderMapper {

    /**
     * Insert a new order
     * @param order the order to insert
     * @return number of rows affected
     */
    int insert(Order order);

    /**
     * Update an existing order
     * @param order the order to update
     * @return number of rows affected
     */
    int update(Order order);

    /**
     * Find order by ID
     * @param orderId the order ID
     * @return the order, or null if not found
     */
    Order findById(@Param("orderId") Long orderId);

    /**
     * Find all orders by user ID
     * @param userId the user ID
     * @return list of orders
     */
    List<Order> findByUserId(@Param("userId") Long userId);

    /**
     * Find orders by symbol and status
     * @param symbol the trading symbol
     * @param status the order status
     * @return list of orders
     */
    List<Order> findBySymbolAndStatus(@Param("symbol") String symbol, @Param("status") OrderStatus status);

    /**
     * Delete order by ID
     * @param orderId the order ID
     * @return number of rows affected
     */
    int deleteById(@Param("orderId") Long orderId);
}
