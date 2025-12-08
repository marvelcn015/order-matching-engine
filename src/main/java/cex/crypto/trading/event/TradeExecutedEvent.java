package cex.crypto.trading.event;

import cex.crypto.trading.domain.Trade;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Event published when a trade is executed
 * Published to trade-output topic after order matching
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradeExecutedEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique message ID for idempotency (UUID)
     */
    private String messageId;

    /**
     * Event timestamp in epoch milliseconds
     */
    private Long timestamp;

    /**
     * Trade ID
     */
    private Long tradeId;

    /**
     * Buy order ID
     */
    private Long buyOrderId;

    /**
     * Sell order ID
     */
    private Long sellOrderId;

    /**
     * Trading symbol (e.g., BTC-USD)
     */
    private String symbol;

    /**
     * Trade execution price
     */
    private BigDecimal price;

    /**
     * Trade quantity
     */
    private BigDecimal quantity;

    /**
     * Taker order ID (order that triggered the match)
     */
    private Long takerOrderId;

    /**
     * Maker order ID (order from the book)
     */
    private Long makerOrderId;

    /**
     * Create event from Trade entity
     *
     * @param trade the trade entity
     * @param takerOrderId the ID of the order that triggered the match
     * @return TradeExecutedEvent
     */
    public static TradeExecutedEvent fromTrade(Trade trade, Long takerOrderId) {
        // Determine maker order ID (the other order in the trade)
        Long makerOrderId = takerOrderId.equals(trade.getBuyOrderId())
                ? trade.getSellOrderId()
                : trade.getBuyOrderId();

        return TradeExecutedEvent.builder()
                .messageId(UUID.randomUUID().toString())
                .timestamp(System.currentTimeMillis())
                .tradeId(trade.getTradeId())
                .buyOrderId(trade.getBuyOrderId())
                .sellOrderId(trade.getSellOrderId())
                .symbol(trade.getSymbol())
                .price(trade.getPrice())
                .quantity(trade.getQuantity())
                .takerOrderId(takerOrderId)
                .makerOrderId(makerOrderId)
                .build();
    }
}
