package cex.crypto.trading.enums;

/**
 * Order status enum representing the lifecycle of an order
 */
public enum OrderStatus {
    /**
     * Order has been submitted but not yet in the order book
     */
    PENDING,

    /**
     * Order is active in the order book waiting for match
     */
    OPEN,

    /**
     * Order has been partially filled
     */
    PARTIALLY_FILLED,

    /**
     * Order has been completely filled
     */
    FILLED,

    /**
     * Order has been cancelled by user
     */
    CANCELLED,

    /**
     * Order has been rejected by the system
     */
    REJECTED
}
