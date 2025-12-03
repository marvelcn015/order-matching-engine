package cex.crypto.trading.service;

import cex.crypto.trading.domain.Trade;
import cex.crypto.trading.mapper.TradeMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Service for Trade entity management
 * Handles trade recording and queries
 */
@Slf4j
@Service
public class TradeService {

    @Autowired
    private TradeMapper tradeMapper;

    /**
     * Create and persist a new trade
     *
     * @param trade the trade to create
     * @return the created trade with generated ID
     */
    public Trade createTrade(Trade trade) {
        if (trade.getCreatedAt() == null) {
            trade.setCreatedAt(LocalDateTime.now());
        }

        int result = tradeMapper.insert(trade);
        if (result > 0) {
            log.info("Trade created: tradeId={}, buyOrderId={}, sellOrderId={}, symbol={}, price={}, qty={}",
                    trade.getTradeId(), trade.getBuyOrderId(), trade.getSellOrderId(),
                    trade.getSymbol(), trade.getPrice(), trade.getQuantity());
            return trade;
        } else {
            throw new RuntimeException("Failed to create trade");
        }
    }

    /**
     * Get trade by ID
     *
     * @param tradeId the trade ID
     * @return the trade, or null if not found
     */
    public Trade getTradeById(Long tradeId) {
        return tradeMapper.findById(tradeId);
    }

    /**
     * Get all trades for a specific order
     *
     * @param orderId the order ID (can be buy or sell order)
     * @return list of trades
     */
    public List<Trade> getTradesByOrderId(Long orderId) {
        return tradeMapper.findByOrderId(orderId);
    }

    /**
     * Get trades by symbol within a time range
     *
     * @param symbol the trading symbol
     * @param startTime start of time range
     * @param endTime end of time range
     * @return list of trades
     */
    public List<Trade> getTradesBySymbol(String symbol, LocalDateTime startTime, LocalDateTime endTime) {
        return tradeMapper.findBySymbol(symbol, startTime, endTime);
    }
}
