package cex.crypto.trading.enums;

/**
 * Order type enum - LIMIT or MARKET
 */
public enum OrderType {
    /**
     * Limit order - executes at specified price or better
     */
    LIMIT,

    /**
     * Market order - executes immediately at best available price
     */
    MARKET
}
