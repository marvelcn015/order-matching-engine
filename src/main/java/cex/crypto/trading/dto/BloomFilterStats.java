package cex.crypto.trading.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Statistics for Bloom Filter monitoring
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BloomFilterStats {
    /**
     * Current number of users in the filter
     */
    private long userFilterSize;

    /**
     * Maximum capacity for user filter
     */
    private long userFilterCapacity;

    /**
     * Utilization percentage for user filter
     */
    private double userFilterUtilization;

    /**
     * Current number of orders in the filter
     */
    private long orderFilterSize;

    /**
     * Maximum capacity for order filter
     */
    private long orderFilterCapacity;

    /**
     * Utilization percentage for order filter
     */
    private double orderFilterUtilization;
}
