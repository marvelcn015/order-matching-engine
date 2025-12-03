package cex.crypto.trading.controller;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.dto.ApiResponse;
import cex.crypto.trading.dto.CreateOrderRequest;
import cex.crypto.trading.dto.OrderResponse;
import cex.crypto.trading.exception.OrderNotFoundException;
import cex.crypto.trading.service.MatchingEngineService;
import cex.crypto.trading.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for Order management
 * Handles order creation, query, and cancellation operations
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/orders")
@Validated
@Tag(name = "Order Management", description = "APIs for order operations")
public class OrderController {

    @Autowired
    private OrderService orderService;

    @Autowired
    private MatchingEngineService matchingEngineService;

    /**
     * Create a new order
     *
     * @param request the create order request
     * @return API response with created order details
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(
        summary = "Create a new order",
        description = "Submit a new LIMIT or MARKET order. LIMIT orders require price, MARKET orders do not."
    )
    public ApiResponse<OrderResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        log.info("Received create order request: {}", request);

        // Validate request
        orderService.validateCreateOrderRequest(request);

        // Create order entity
        Order order = Order.builder()
                .userId(request.getUserId())
                .symbol(request.getSymbol())
                .side(request.getSide())
                .type(request.getType())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .build();

        // Process order through matching engine
        matchingEngineService.processOrder(order);

        // Convert to response DTO
        OrderResponse response = OrderResponse.fromOrder(order);

        log.info("Order created successfully: orderId={}", order.getOrderId());
        return ApiResponse.success("Order created successfully", response);
    }

    /**
     * Query order by ID
     *
     * @param orderId the order ID
     * @return API response with order details
     */
    @GetMapping("/{orderId}")
    @Operation(
        summary = "Query order by ID",
        description = "Retrieve order details by order ID"
    )
    public ApiResponse<OrderResponse> getOrder(
            @Parameter(description = "Order ID", required = true)
            @PathVariable @NotNull Long orderId) {

        log.debug("Querying order: orderId={}", orderId);

        Order order = orderService.getOrderById(orderId);
        if (order == null) {
            throw new OrderNotFoundException("Order not found: " + orderId);
        }

        OrderResponse response = OrderResponse.fromOrder(order);
        return ApiResponse.success(response);
    }

    /**
     * Cancel an order
     *
     * @param orderId the order ID to cancel
     * @param userId the user ID (for authorization)
     * @return API response with cancelled order details
     */
    @DeleteMapping("/{orderId}")
    @Operation(
        summary = "Cancel an order",
        description = "Cancel an existing order. Only the order owner can cancel their orders."
    )
    public ApiResponse<OrderResponse> cancelOrder(
            @Parameter(description = "Order ID", required = true)
            @PathVariable @NotNull Long orderId,
            @Parameter(description = "User ID for authorization", required = true)
            @RequestParam @NotNull Long userId) {

        log.info("Received cancel order request: orderId={}, userId={}", orderId, userId);

        Order cancelledOrder = orderService.cancelOrder(orderId, userId);
        OrderResponse response = OrderResponse.fromOrder(cancelledOrder);

        log.info("Order cancelled successfully: orderId={}", orderId);
        return ApiResponse.success("Order cancelled successfully", response);
    }
}
