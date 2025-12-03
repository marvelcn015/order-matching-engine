package cex.crypto.trading.mapper;

import cex.crypto.trading.domain.OrderBook;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis mapper interface for OrderBook entity
 */
@Mapper
public interface OrderBookMapper {

    /**
     * Insert a new order book
     * @param orderBook the order book to insert
     * @return number of rows affected
     */
    int insert(OrderBook orderBook);

    /**
     * Update an existing order book with optimistic locking
     * @param orderBook the order book to update
     * @return number of rows affected (0 if version conflict)
     */
    int update(OrderBook orderBook);

    /**
     * Find order book by symbol
     * @param symbol the trading symbol
     * @return the order book, or null if not found
     */
    OrderBook findBySymbol(@Param("symbol") String symbol);

    /**
     * Find all order books (for testing)
     * @return list of all order books
     */
    List<OrderBook> findAll();
}
