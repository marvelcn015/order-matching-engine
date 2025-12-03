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
 * 訂單簿深度響應 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "訂單簿深度響應")
public class OrderBookDepthResponse {

    @Schema(description = "交易對符號", example = "BTC-USD")
    private String symbol;

    @Schema(description = "買單深度（價格降序）")
    private List<PriceLevel> bids;

    @Schema(description = "賣單深度（價格升序）")
    private List<PriceLevel> asks;

    @Schema(description = "最高買價", example = "50000.00")
    private BigDecimal bestBid;

    @Schema(description = "最低賣價", example = "50001.00")
    private BigDecimal bestAsk;

    @Schema(description = "買賣差價", example = "1.00")
    private BigDecimal spread;

    @Schema(description = "時間戳")
    private LocalDateTime timestamp;

    /**
     * 價格檔位 DTO
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Schema(description = "價格檔位")
    public static class PriceLevel {

        @Schema(description = "價格", example = "50000.00")
        private BigDecimal price;

        @Schema(description = "該價格的總量", example = "10.5")
        private BigDecimal quantity;

        @Schema(description = "該價格的訂單數量", example = "5")
        private Integer orderCount;
    }
}
