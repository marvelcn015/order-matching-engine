package cex.crypto.trading.controller;

import cex.crypto.trading.domain.UserBalance;
import cex.crypto.trading.dto.ApiResponse;
import cex.crypto.trading.dto.UpdateBalanceRequest;
import cex.crypto.trading.dto.UserBalanceResponse;
import cex.crypto.trading.service.UserBalanceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller for User Balance management
 * Handles balance query and update operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/balances")
@Validated
@Tag(name = "User Balance", description = "APIs for user balance operations")
public class UserBalanceController {

    @Autowired
    private UserBalanceService balanceService;

    /**
     * Get all balances for a user
     *
     * @param userId the user ID
     * @return API response with list of balances
     */
    @GetMapping("/{userId}")
    @Operation(summary = "Get all balances for a user", description = "Retrieve all currency balances for a user")
    public ApiResponse<List<UserBalanceResponse>> getUserBalances(
            @Parameter(description = "User ID", required = true)
            @PathVariable @NotNull Long userId) {
        log.debug("Getting balances for userId={}", userId);
        List<UserBalance> balances = balanceService.getUserBalances(userId);
        List<UserBalanceResponse> responses = balances.stream()
                .map(UserBalanceResponse::fromUserBalance)
                .collect(Collectors.toList());
        return ApiResponse.success(responses);
    }

    /**
     * Get balance for a specific currency
     *
     * @param userId the user ID
     * @param currency the currency code
     * @return API response with balance details
     */
    @GetMapping("/{userId}/{currency}")
    @Operation(summary = "Get balance for a currency", description = "Retrieve balance for a specific currency")
    public ApiResponse<UserBalanceResponse> getBalance(
            @Parameter(description = "User ID", required = true)
            @PathVariable @NotNull Long userId,
            @Parameter(description = "Currency code", required = true)
            @PathVariable @NotBlank String currency) {
        log.debug("Getting balance: userId={}, currency={}", userId, currency);
        UserBalance balance = balanceService.getBalance(userId, currency);
        UserBalanceResponse response = UserBalanceResponse.fromUserBalance(balance);
        return ApiResponse.success(response);
    }

    /**
     * Update balance
     *
     * @param request the update balance request
     * @return API response with updated balance
     */
    @PutMapping("/update")
    @Operation(summary = "Update balance", description = "Add or subtract from available balance")
    public ApiResponse<UserBalanceResponse> updateBalance(@Valid @RequestBody UpdateBalanceRequest request) {
        log.info("Updating balance: userId={}, currency={}, amount={}",
                 request.getUserId(), request.getCurrency(), request.getAmount());
        UserBalance balance = balanceService.updateAvailableBalance(
                request.getUserId(),
                request.getCurrency(),
                request.getAmount()
        );
        UserBalanceResponse response = UserBalanceResponse.fromUserBalance(balance);
        return ApiResponse.success("Balance updated successfully", response);
    }

    /**
     * Get cache statistics
     *
     * @return API response with cache stats
     */
    @GetMapping("/cache/stats")
    @Operation(summary = "Get cache statistics", description = "Retrieve Caffeine cache statistics")
    public ApiResponse<String> getCacheStats() {
        String stats = balanceService.getCacheStats();
        return ApiResponse.success(stats);
    }
}
