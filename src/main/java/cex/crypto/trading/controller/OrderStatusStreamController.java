package cex.crypto.trading.controller;

import cex.crypto.trading.service.sse.SseEmitterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

/**
 * Controller for Server-Sent Events (SSE) order status streaming
 * Provides real-time order status updates to clients
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/stream")
@Validated
@Tag(name = "Order Status Stream", description = "Real-time order status updates via SSE")
public class OrderStatusStreamController {

    @Autowired
    private SseEmitterRegistry sseEmitterRegistry;

    /**
     * SSE endpoint for real-time order status updates
     * Client connects to this endpoint and receives order status changes as they happen
     *
     * Usage:
     * <pre>
     * const eventSource = new EventSource('/api/v1/stream/order-status?userId=1');
     * eventSource.addEventListener('order-status-update', (event) => {
     *   const orderStatus = JSON.parse(event.data);
     *   console.log('Order status:', orderStatus);
     * });
     * </pre>
     *
     * @param userId the user ID to stream updates for
     * @param timeout connection timeout in milliseconds (default: 5 minutes)
     * @return SseEmitter for streaming events
     */
    @GetMapping(value = "/order-status", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "Stream order status updates (SSE)",
        description = "Establishes a Server-Sent Events connection to receive real-time order status updates. " +
                      "The connection remains open and pushes updates as orders are matched, filled, or cancelled. " +
                      "Default timeout is 5 minutes (300000ms)."
    )
    public SseEmitter streamOrderStatus(
            @Parameter(description = "User ID to receive updates for", required = true)
            @RequestParam @NotNull Long userId,
            @Parameter(description = "Connection timeout in milliseconds (default: 300000ms / 5min)")
            @RequestParam(required = false, defaultValue = "300000") Long timeout) {

        log.info("SSE connection request: userId={}, timeout={}ms", userId, timeout);

        // Create SSE emitter with timeout
        SseEmitter emitter = new SseEmitter(timeout);

        // Register emitter in registry
        sseEmitterRegistry.addEmitter(userId, emitter);

        // Handle completion (client closes connection)
        emitter.onCompletion(() -> {
            log.info("SSE connection completed: userId={}", userId);
            sseEmitterRegistry.removeEmitter(userId, emitter);
        });

        // Handle timeout (no activity for timeout duration)
        emitter.onTimeout(() -> {
            log.warn("SSE connection timeout: userId={}, timeout={}ms", userId, timeout);
            sseEmitterRegistry.removeEmitter(userId, emitter);
            emitter.complete();
        });

        // Handle error (network error, client disconnect, etc.)
        emitter.onError((ex) -> {
            log.error("SSE connection error: userId={}, error={}", userId, ex.getMessage());
            sseEmitterRegistry.removeEmitter(userId, emitter);
        });

        // Send initial connection success event
        try {
            String connectionId = UUID.randomUUID().toString();
            emitter.send(SseEmitter.event()
                .name("connected")
                .data("Connected to order status stream")
                .id(connectionId)
            );

            log.info("SSE connection established: userId={}, connectionId={}, activeConnections={}",
                    userId, connectionId, sseEmitterRegistry.getConnectionCount(userId));

        } catch (IOException e) {
            log.error("Failed to send initial SSE event: userId={}, error={}", userId, e.getMessage());
            sseEmitterRegistry.removeEmitter(userId, emitter);
        }

        return emitter;
    }

    /**
     * Health check endpoint for SSE connections
     * Returns current SSE connection statistics
     *
     * @return connection statistics
     */
    @GetMapping("/order-status/stats")
    @Operation(
        summary = "Get SSE connection statistics",
        description = "Returns current statistics about active SSE connections"
    )
    public SseConnectionStats getConnectionStats() {
        return new SseConnectionStats(
            sseEmitterRegistry.getTotalUserCount(),
            sseEmitterRegistry.getTotalConnectionCount()
        );
    }

    /**
     * DTO for SSE connection statistics
     */
    public record SseConnectionStats(
        int totalUsers,
        int totalConnections
    ) {}
}
