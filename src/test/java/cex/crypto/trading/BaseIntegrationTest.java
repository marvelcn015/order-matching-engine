package cex.crypto.trading;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.domain.OrderBook;
import cex.crypto.trading.domain.Trade;
import cex.crypto.trading.enums.OrderSide;
import cex.crypto.trading.enums.OrderStatus;
import cex.crypto.trading.enums.OrderType;
import cex.crypto.trading.mapper.OrderBookMapper;
import cex.crypto.trading.mapper.OrderMapper;
import cex.crypto.trading.mapper.TradeMapper;
import cex.crypto.trading.service.OrderBookService;
import cex.crypto.trading.service.OrderService;
import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Base class for integration tests
 * Provides common setup, teardown, and utility methods
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public abstract class BaseIntegrationTest {

    @Autowired
    protected OrderMapper orderMapper;

    @Autowired
    protected TradeMapper tradeMapper;

    @Autowired
    protected OrderBookMapper orderBookMapper;

    @Autowired
    protected OrderService orderService;

    @Autowired
    protected OrderBookService orderBookService;

    /**
     * Cleanup method run after each test
     * Ensures clean state between tests
     */
    @AfterEach
    void baseCleanup() {
        // Clean up test data (in reverse order due to FK constraints)
        // Delete trades first, then orders
        List<Trade> trades = tradeMapper.findAll();
        for (Trade trade : trades) {
            tradeMapper.deleteById(trade.getTradeId());
        }

        List<Order> orders = orderMapper.findAll();
        for (Order order : orders) {
            orderMapper.deleteById(order.getOrderId());
        }

        // Reset order books to empty state
        List<OrderBook> orderBooks = orderBookMapper.findAll();
        for (OrderBook orderBook : orderBooks) {
            orderBook.getBuyOrders().clear();
            orderBook.getSellOrders().clear();
            orderBook.setVersion(0);
            orderBookMapper.update(orderBook);
        }
    }

    // ============= ASSERTION UTILITIES =============

    /**
     * Assert order state matches expected values
     */
    protected void assertOrderState(Order order,
                                     String expectedStatus,
                                     String expectedFilledQty,
                                     String expectedRemainingQty) {
        assertThat(order.getStatus().name()).isEqualTo(expectedStatus);
        assertThat(order.getFilledQuantity()).isEqualByComparingTo(expectedFilledQty);
        assertThat(order.getRemainingQuantity()).isEqualByComparingTo(expectedRemainingQty);
    }

    /**
     * Assert trade matches expected values
     */
    protected void assertTrade(Trade trade,
                                Long expectedBuyOrderId,
                                Long expectedSellOrderId,
                                String expectedPrice,
                                String expectedQuantity) {
        assertThat(trade.getBuyOrderId()).isEqualTo(expectedBuyOrderId);
        assertThat(trade.getSellOrderId()).isEqualTo(expectedSellOrderId);
        assertThat(trade.getPrice()).isEqualByComparingTo(expectedPrice);
        assertThat(trade.getQuantity()).isEqualByComparingTo(expectedQuantity);
    }

    /**
     * Assert order book best prices
     */
    protected void assertOrderBookPrices(OrderBook orderBook,
                                          String expectedBestBid,
                                          String expectedBestAsk) {
        if (expectedBestBid != null) {
            assertThat(orderBook.getBestBid()).isEqualByComparingTo(expectedBestBid);
        } else {
            assertThat(orderBook.getBestBid()).isNull();
        }

        if (expectedBestAsk != null) {
            assertThat(orderBook.getBestAsk()).isEqualByComparingTo(expectedBestAsk);
        } else {
            assertThat(orderBook.getBestAsk()).isNull();
        }
    }

    /**
     * Assert database contains expected number of records
     */
    protected void assertDatabaseCounts(int expectedOrders, int expectedTrades) {
        List<Order> orders = orderMapper.findAll();
        List<Trade> trades = tradeMapper.findAll();

        assertThat(orders).hasSize(expectedOrders);
        assertThat(trades).hasSize(expectedTrades);
    }

    // ============= TEST DATA BUILDERS =============

    /**
     * Create and add order to order book (helper for tests)
     */
    protected Order createAndAddToBook(Long userId, OrderSide side,
                                        String price, String quantity) {
        Order order = Order.builder()
                .userId(userId)
                .symbol("BTC-USD")
                .side(side)
                .type(OrderType.LIMIT)
                .price(new BigDecimal(price))
                .quantity(new BigDecimal(quantity))
                .filledQuantity(BigDecimal.ZERO)
                .status(OrderStatus.PENDING)
                .build();

        order = orderService.createOrder(order);
        order.setStatus(OrderStatus.OPEN);
        orderService.updateOrder(order);

        OrderBook orderBook = orderBookService.getOrCreateOrderBook("BTC-USD");
        orderBookService.addOrderToBook(orderBook, order);
        orderBookService.saveOrderBook(orderBook);

        return order;
    }
}
