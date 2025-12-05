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
 * Create order request DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Create order request")
public class CreateOrderRequest {

    @NotNull(message = "User ID cannot be null")
    @Schema(description = "User ID", example = "1", required = true)
    private Long userId;

    @NotBlank(message = "Trading pair symbol cannot be blank")
    @Pattern(regexp = "^[A-Z]+-[A-Z]+$", message = "Invalid trading pair format, should be XXX-XXX")
    @Schema(description = "Trading pair symbol", example = "BTC-USD", required = true)
    private String symbol;

    @NotNull(message = "Order side cannot be null")
    @Schema(description = "Order side", example = "BUY", required = true, allowableValues = {"BUY", "SELL"})
    private OrderSide side;

    @NotNull(message = "Order type cannot be null")
    @Schema(description = "Order type", example = "LIMIT", required = true, allowableValues = {"LIMIT", "MARKET"})
    private OrderType type;

    @DecimalMin(value = "0.01", message = "Price must be greater than or equal to 0.01")
    @Digits(integer = 12, fraction = 8, message = "Price must have at most 12 integer digits and 8 fractional digits")
    @Schema(description = "Price (required for LIMIT orders, not required for MARKET orders)", example = "50000.00")
    private BigDecimal price;

    @NotNull(message = "Quantity cannot be null")
    @DecimalMin(value = "0.001", message = "Quantity must be greater than or equal to 0.001")
    @Digits(integer = 12, fraction = 8, message = "Quantity must have at most 12 integer digits and 8 fractional digits")
    @Schema(description = "Quantity", example = "1.5", required = true)
    private BigDecimal quantity;
}
