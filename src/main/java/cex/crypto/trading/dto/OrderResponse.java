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
 * Order response DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Order response")
public class OrderResponse {

    @Schema(description = "Order ID", example = "12345")
    private Long orderId;

    @Schema(description = "User ID", example = "1")
    private Long userId;

    @Schema(description = "Trading pair symbol", example = "BTC-USD")
    private String symbol;

    @Schema(description = "Order side", example = "BUY")
    private OrderSide side;

    @Schema(description = "Order type", example = "LIMIT")
    private OrderType type;

    @Schema(description = "Price", example = "50000.00")
    private BigDecimal price;

    @Schema(description = "Quantity", example = "1.5")
    private BigDecimal quantity;

    @Schema(description = "Filled quantity", example = "0.5")
    private BigDecimal filledQuantity;

    @Schema(description = "Remaining quantity", example = "1.0")
    private BigDecimal remainingQuantity;

    @Schema(description = "Order status", example = "PARTIALLY_FILLED")
    private OrderStatus status;

    @Schema(description = "Created timestamp", example = "2025-01-15T10:30:00")
    private LocalDateTime createdAt;

    @Schema(description = "Updated timestamp", example = "2025-01-15T10:30:01")
    private LocalDateTime updatedAt;

    /**
     * Convert Order entity to OrderResponse DTO
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
