package cex.crypto.trading.dto;

import cex.crypto.trading.enums.OrderSide;
import cex.crypto.trading.enums.OrderType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 創建訂單請求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "創建訂單請求")
public class CreateOrderRequest {

    @NotNull(message = "用戶 ID 不能為空")
    @Schema(description = "用戶 ID", example = "1", required = true)
    private Long userId;

    @NotBlank(message = "交易對符號不能為空")
    @Pattern(regexp = "^[A-Z]+-[A-Z]+$", message = "交易對格式不正確，應為 XXX-XXX 格式")
    @Schema(description = "交易對符號", example = "BTC-USD", required = true)
    private String symbol;

    @NotNull(message = "訂單方向不能為空")
    @Schema(description = "訂單方向", example = "BUY", required = true, allowableValues = {"BUY", "SELL"})
    private OrderSide side;

    @NotNull(message = "訂單類型不能為空")
    @Schema(description = "訂單類型", example = "LIMIT", required = true, allowableValues = {"LIMIT", "MARKET"})
    private OrderType type;

    @DecimalMin(value = "0.01", message = "價格必須大於等於 0.01")
    @Digits(integer = 12, fraction = 8, message = "價格最多 12 位整數和 8 位小數")
    @Schema(description = "價格（LIMIT 訂單必填，MARKET 訂單不填）", example = "50000.00")
    private BigDecimal price;

    @NotNull(message = "數量不能為空")
    @DecimalMin(value = "0.001", message = "數量必須大於等於 0.001")
    @Digits(integer = 12, fraction = 8, message = "數量最多 12 位整數和 8 位小數")
    @Schema(description = "數量", example = "1.5", required = true)
    private BigDecimal quantity;
}
