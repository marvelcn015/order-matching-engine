package cex.crypto.trading.controller;

import cex.crypto.trading.dto.ApiResponse;
import cex.crypto.trading.dto.OrderBookDepthResponse;
import cex.crypto.trading.service.OrderBookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Order Book management
 * Handles order book depth queries
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orderbook")
@Validated
@Tag(name = "Order Book", description = "APIs for order book operations")
public class OrderBookController {

    @Autowired
    private OrderBookService orderBookService;

    /**
     * Query order book depth by symbol
     *
     * @param symbol the trading symbol
     * @param limit the number of price levels to return (optional, default: all)
     * @return API response with order book depth
     */
    @GetMapping("/{symbol}")
    @Operation(
        summary = "Query order book depth",
        description = "Retrieve aggregated order book depth for a trading symbol. " +
                      "Returns bids (descending by price) and asks (ascending by price) " +
                      "with total quantity and order count at each price level."
    )
    public ApiResponse<OrderBookDepthResponse> getOrderBookDepth(
            @Parameter(description = "Trading symbol (e.g., BTC-USD)", required = true)
            @PathVariable @NotBlank String symbol,
            @Parameter(description = "Number of price levels to return (1-100, optional)")
            @RequestParam(required = false)
            @Min(value = 1, message = "Limit must be at least 1")
            @Max(value = 100, message = "Limit cannot exceed 100")
            Integer limit) {

        log.debug("Querying order book depth: symbol={}, limit={}", symbol, limit);

        OrderBookDepthResponse depth = orderBookService.getOrderBookDepth(symbol, limit);

        return ApiResponse.success(depth);
    }
}
