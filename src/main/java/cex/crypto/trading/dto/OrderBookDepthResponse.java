package cex.crypto.trading.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Order book depth response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Order book depth response")
public class OrderBookDepthResponse {

    @Schema(description = "Trading pair symbol", example = "BTC-USD")
    private String symbol;

    @Schema(description = "Buy order depth (descending price)")
    private List<PriceLevel> bids;

    @Schema(description = "Sell order depth (ascending price)")
    private List<PriceLevel> asks;

    @Schema(description = "Best bid price", example = "50000.00")
    private BigDecimal bestBid;

    @Schema(description = "Best ask price", example = "50001.00")
    private BigDecimal bestAsk;

    @Schema(description = "Bid-ask spread", example = "1.00")
    private BigDecimal spread;

    @Schema(description = "Timestamp")
    private LocalDateTime timestamp;

    /**
     * Price level DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "Price level")
    public static class PriceLevel {

        @Schema(description = "Price", example = "50000.00")
        private BigDecimal price;

        @Schema(description = "Total quantity at this price", example = "10.5")
        private BigDecimal quantity;

        @Schema(description = "Number of orders at this price", example = "5")
        private Integer orderCount;
    }
}
