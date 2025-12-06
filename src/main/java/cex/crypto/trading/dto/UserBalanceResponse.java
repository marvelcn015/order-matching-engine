package cex.crypto.trading.dto;

import cex.crypto.trading.domain.UserBalance;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserBalanceResponse {
    private Long userId;
    private String currency;
    private BigDecimal availableBalance;
    private BigDecimal frozenBalance;
    private BigDecimal totalBalance;
    private LocalDateTime updatedAt;

    public static UserBalanceResponse fromUserBalance(UserBalance balance) {
        return UserBalanceResponse.builder()
                .userId(balance.getUserId())
                .currency(balance.getCurrency())
                .availableBalance(balance.getAvailableBalance())
                .frozenBalance(balance.getFrozenBalance())
                .totalBalance(balance.getTotalBalance())
                .updatedAt(balance.getUpdatedAt())
                .build();
    }
}
