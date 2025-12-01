package cex.crypto.trading.mapper;

import cex.crypto.trading.domain.Trade;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MyBatis mapper interface for Trade entity
 */
@Mapper
public interface TradeMapper {

    /**
     * Insert a new trade
     * @param trade the trade to insert
     * @return number of rows affected
     */
    int insert(Trade trade);

    /**
     * Find trade by ID
     * @param tradeId the trade ID
     * @return the trade, or null if not found
     */
    Trade findById(@Param("tradeId") Long tradeId);

    /**
     * Find all trades by order ID
     * @param orderId the order ID (can be buy or sell order)
     * @return list of trades
     */
    List<Trade> findByOrderId(@Param("orderId") Long orderId);

    /**
     * Find trades by symbol within time range
     * @param symbol the trading symbol
     * @param startTime start of time range
     * @param endTime end of time range
     * @return list of trades
     */
    List<Trade> findBySymbol(@Param("symbol") String symbol,
                             @Param("startTime") LocalDateTime startTime,
                             @Param("endTime") LocalDateTime endTime);
}
