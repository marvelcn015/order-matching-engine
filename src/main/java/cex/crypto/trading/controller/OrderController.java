package cex.crypto.trading.controller;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.dto.ApiResponse;
import cex.crypto.trading.dto.CreateOrderRequest;
import cex.crypto.trading.dto.OrderAcceptedResponse;
import cex.crypto.trading.dto.OrderResponse;
import cex.crypto.trading.enums.OrderStatus;
import cex.crypto.trading.event.OrderCreatedEvent;
import cex.crypto.trading.exception.KafkaPublishException;
import cex.crypto.trading.exception.OrderNotFoundException;
import cex.crypto.trading.service.OrderService;
import cex.crypto.trading.service.kafka.OrderEventProducerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.kafka.support.SendResult;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.concurrent.CompletableFuture;

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
    private OrderEventProducerService orderEventProducerService;

    /**
     * Create a new order (ASYNC)
     * Returns 202 ACCEPTED immediately after order is queued for processing
     *
     * @param request the create order request
     * @return API response with order acceptance confirmation
     */
    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(
        summary = "Create a new order (async)",
        description = "Submit a new LIMIT or MARKET order for async processing. Returns immediately with order ID. " +
                      "Use GET /api/v1/orders/{orderId} to check status or connect to SSE for real-time updates."
    )
    public ApiResponse<OrderAcceptedResponse> createOrder(
            @Valid @RequestBody CreateOrderRequest request) {

        String correlationId = java.util.UUID.randomUUID().toString();
        log.info("Received create order request: {}, correlationId={}", request, correlationId);

        // 1. Validate request (synchronous)
        orderService.validateCreateOrderRequest(request);

        // 2. Create order entity with PENDING status
        Order order = Order.builder()
                .userId(request.getUserId())
                .symbol(request.getSymbol())
                .side(request.getSide())
                .type(request.getType())
                .price(request.getPrice())
                .quantity(request.getQuantity())
                .build();
        orderService.createOrder(order); // Persists with PENDING status

        // 3. Publish to Kafka (wait for confirmation to ensure reliable submission)
        try {
            CompletableFuture<SendResult<String, OrderCreatedEvent>> future =
                    orderEventProducerService.publishOrder(order, correlationId);

            // Wait for Kafka send to complete (with timeout)
            future.get(5, java.util.concurrent.TimeUnit.SECONDS);

        } catch (Exception e) {
            log.error("Failed to publish order to Kafka: orderId={}, error={}",
                    order.getOrderId(), e.getMessage(), e);

            // Update order status to FAILED
            order.setStatus(OrderStatus.FAILED);
            orderService.updateOrder(order);

            throw new KafkaPublishException("Failed to submit order for processing", e);
        }

        // 4. Return 202 ACCEPTED immediately
        OrderAcceptedResponse response = OrderAcceptedResponse.builder()
                .orderId(order.getOrderId())
                .status("PENDING")
                .message("Order accepted for processing")
                .correlationId(correlationId)
                .build();

        log.info("Order accepted: orderId={}, correlationId={}", order.getOrderId(), correlationId);
        return ApiResponse.success("Order accepted for processing", response);
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
