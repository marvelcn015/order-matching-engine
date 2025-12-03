package cex.crypto.trading.dto;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.enums.OrderSide;
import cex.crypto.trading.enums.OrderStatus;
import cex.crypto.trading.enums.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 訂單響應 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "訂單響應")
public class OrderResponse {

    @Schema(description = "訂單 ID", example = "12345")
    private Long orderId;

    @Schema(description = "用戶 ID", example = "1")
    private Long userId;

    @Schema(description = "交易對符號", example = "BTC-USD")
    private String symbol;

    @Schema(description = "訂單方向", example = "BUY")
    private OrderSide side;

    @Schema(description = "訂單類型", example = "LIMIT")
    private OrderType type;

    @Schema(description = "價格", example = "50000.00")
    private BigDecimal price;

    @Schema(description = "數量", example = "1.5")
    private BigDecimal quantity;

    @Schema(description = "已成交數量", example = "0.5")
    private BigDecimal filledQuantity;

    @Schema(description = "剩餘數量", example = "1.0")
    private BigDecimal remainingQuantity;

    @Schema(description = "訂單狀態", example = "PARTIALLY_FILLED")
    private OrderStatus status;

    @Schema(description = "創建時間", example = "2025-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "更新時間", example = "2025-01-15T10:30:01")
    private LocalDateTime updatedAt;

    /**
     * 從 Order 實體轉換為 OrderResponse DTO
     */
    public static OrderResponse fromOrder(Order order) {
        if (order == null) {
            return null;
        }

        return OrderResponse.builder()
                .orderId(order.getOrderId())
                .userId(order.getUserId())
                .symbol(order.getSymbol())
                .side(order.getSide())
                .type(order.getType())
                .price(order.getPrice())
                .quantity(order.getQuantity())
                .filledQuantity(order.getFilledQuantity())
                .remainingQuantity(order.getRemainingQuantity())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
