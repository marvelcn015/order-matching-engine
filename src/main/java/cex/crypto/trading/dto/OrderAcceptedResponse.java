package cex.crypto.trading.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for async order submission (HTTP 202 ACCEPTED)
 * Returned immediately after order is accepted for processing
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderAcceptedResponse {

    /**
     * Order ID for tracking
     */
    private Long orderId;

    /**
     * Current status (always "PENDING" on acceptance)
     */
    private String status;

    /**
     * Message to the user
     */
    private String message;

    /**
     * Correlation ID for request tracing
     */
    private String correlationId;
}
