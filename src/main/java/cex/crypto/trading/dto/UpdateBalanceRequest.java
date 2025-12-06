package cex.crypto.trading.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Update balance request")
public class UpdateBalanceRequest {
    @NotNull(message = "User ID cannot be null")
    @Schema(description = "User ID", example = "1")
    private Long userId;

    @NotBlank(message = "Currency cannot be blank")
    @Schema(description = "Currency code", example = "USD")
    private String currency;

    @NotNull(message = "Amount cannot be null")
    @Schema(description = "Amount to add (positive) or subtract (negative)", example = "100.00")
    private BigDecimal amount;
}
