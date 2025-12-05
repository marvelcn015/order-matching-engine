package cex.crypto.trading.testutil;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.enums.OrderSide;
import cex.crypto.trading.enums.OrderStatus;
import cex.crypto.trading.enums.OrderType;

import java.math.BigDecimal;

/**
 * Fluent builder for creating test orders
 * Provides sensible defaults for testing
 */
public class OrderTestBuilder {
    private Long userId = 1L;
    private String symbol = "BTC-USD";
    private OrderSide side = OrderSide.BUY;
    private OrderType type = OrderType.LIMIT;
    private BigDecimal price = new BigDecimal("50000.00");
    private BigDecimal quantity = new BigDecimal("1.0");
    private OrderStatus status = OrderStatus.PENDING;
    private BigDecimal filledQuantity = BigDecimal.ZERO;

    public static OrderTestBuilder limit() {
        return new OrderTestBuilder().type(OrderType.LIMIT);
    }

    public static OrderTestBuilder market() {
        return new OrderTestBuilder().type(OrderType.MARKET).price(null);
    }

    public OrderTestBuilder userId(Long userId) {
        this.userId = userId;
        return this;
    }

    public OrderTestBuilder symbol(String symbol) {
        this.symbol = symbol;
        return this;
    }

    public OrderTestBuilder buy() {
        this.side = OrderSide.BUY;
        return this;
    }

    public OrderTestBuilder sell() {
        this.side = OrderSide.SELL;
        return this;
    }

    public OrderTestBuilder type(OrderType type) {
        this.type = type;
        return this;
    }

    public OrderTestBuilder price(String price) {
        this.price = price != null ? new BigDecimal(price) : null;
        return this;
    }

    public OrderTestBuilder quantity(String quantity) {
        this.quantity = new BigDecimal(quantity);
        return this;
    }

    public OrderTestBuilder status(OrderStatus status) {
        this.status = status;
        return this;
    }

    public Order build() {
        return Order.builder()
                .userId(userId)
                .symbol(symbol)
                .side(side)
                .type(type)
                .price(price)
                .quantity(quantity)
                .status(status)
                .filledQuantity(filledQuantity)
                .build();
    }
}
