package cex.crypto.trading.service;

import cex.crypto.trading.BaseIntegrationTest;
import cex.crypto.trading.domain.Order;
import cex.crypto.trading.domain.OrderBook;
import cex.crypto.trading.domain.Trade;
import cex.crypto.trading.dto.MatchResult;
import cex.crypto.trading.enums.OrderSide;
import cex.crypto.trading.enums.OrderStatus;
import cex.crypto.trading.testutil.OrderTestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for MatchingEngineService
 * Tests core order matching scenarios with real database
 */
@DisplayName("Matching Engine Integration Tests")
class MatchingEngineIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MatchingEngineService matchingEngineService;

    @Test
    @DisplayName("Scenario 1: LIMIT order exact match - both orders fully filled")
    void testLimitOrderExactMatch_FullFill() {
        // GIVEN: Sell order in book
        Order sellOrder = createAndAddToBook(1L, OrderSide.SELL, "50000.00", "1.0");

        // WHEN: Incoming buy order matches exactly
        Order buyOrder = orderService.createOrder(
            OrderTestBuilder.limit()
                .userId(2L)
                .buy()
                .price("50000.00")
                .quantity("1.0")
                .build()
        );

        MatchResult result = matchingEngineService.processOrder(buyOrder);

        // THEN: Full match
        assertThat(result.isFullyMatched()).isTrue();
        assertThat(result.getTrades()).hasSize(1);
        assertThat(result.getModifiedOrders()).hasSize(1);

        // Verify incoming order
        assertOrderState(result.getUpdatedOrder(), "FILLED", "1.0", "0.0");

        // Verify book order
        assertOrderState(result.getModifiedOrders().get(0), "FILLED", "1.0", "0.0");

        // Verify trade
        Trade trade = result.getTrades().get(0);
        assertTrade(trade, buyOrder.getOrderId(), sellOrder.getOrderId(),
                    "50000.00", "1.0");

        // Verify order book is empty
        OrderBook orderBook = orderBookService.getOrderBookBySymbol("BTC-USD");
        assertThat(orderBook.getSellOrders()).isEmpty();
        assertThat(orderBook.getBuyOrders()).isEmpty();

        // Verify database persistence
        assertDatabaseCounts(2, 1);
    }

    @Test
    @DisplayName("Scenario 2: LIMIT order partial fill - incoming order partially filled")
    void testLimitOrderPartialFill_IncomingOrder() {
        // GIVEN: Sell order with less quantity in book
        Order sellOrder = createAndAddToBook(1L, OrderSide.SELL, "50000.00", "0.5");

        // WHEN: Incoming buy order with larger quantity
        Order buyOrder = orderService.createOrder(
            OrderTestBuilder.limit()
                .userId(2L)
                .buy()
                .price("50000.00")
                .quantity("1.0")
                .build()
        );

        MatchResult result = matchingEngineService.processOrder(buyOrder);

        // THEN: Partial match
        assertThat(result.isFullyMatched()).isFalse();
        assertThat(result.getTrades()).hasSize(1);

        // Verify incoming order is partially filled
        assertOrderState(result.getUpdatedOrder(), "PARTIALLY_FILLED", "0.5", "0.5");

        // Verify book order is fully filled
        assertOrderState(result.getModifiedOrders().get(0), "FILLED", "0.5", "0.0");

        // Verify order book has remaining buy order
        OrderBook orderBook = orderBookService.getOrderBookBySymbol("BTC-USD");
        assertThat(orderBook.getSellOrders()).isEmpty();
        assertThat(orderBook.getBuyOrders()).hasSize(1);
        assertOrderBookPrices(orderBook, "50000.00", null);

        // Verify remaining quantity in book
        BigDecimal totalBuyQty = orderBook.getBuyOrders()
            .get(new BigDecimal("50000.00"))
            .stream()
            .map(Order::getRemainingQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(totalBuyQty).isEqualByComparingTo("0.5");

        assertDatabaseCounts(2, 1);
    }

    @Test
    @DisplayName("Scenario 3: LIMIT order crosses multiple price levels")
    void testLimitOrderCrossesMultiplePriceLevels() {
        // GIVEN: Multiple sell orders at different price levels
        Order sell1 = createAndAddToBook(1L, OrderSide.SELL, "50000.00", "0.3");
        Order sell2 = createAndAddToBook(2L, OrderSide.SELL, "50100.00", "0.5");
        Order sell3 = createAndAddToBook(3L, OrderSide.SELL, "50200.00", "0.4");

        // WHEN: Buy order crosses two price levels (but not the third)
        Order buyOrder = orderService.createOrder(
            OrderTestBuilder.limit()
                .userId(4L)
                .buy()
                .price("50150.00")
                .quantity("1.0")
                .build()
        );

        MatchResult result = matchingEngineService.processOrder(buyOrder);

        // THEN: Partial match across two levels
        assertThat(result.isFullyMatched()).isFalse();
        assertThat(result.getTrades()).hasSize(2);
        assertThat(result.getModifiedOrders()).hasSize(2);

        // Verify trades (in price-time priority order)
        assertTrade(result.getTrades().get(0), buyOrder.getOrderId(), sell1.getOrderId(),
                    "50000.00", "0.3");
        assertTrade(result.getTrades().get(1), buyOrder.getOrderId(), sell2.getOrderId(),
                    "50100.00", "0.5");

        // Verify incoming order
        assertOrderState(result.getUpdatedOrder(), "PARTIALLY_FILLED", "0.8", "0.2");

        // Verify order book state
        OrderBook orderBook = orderBookService.getOrderBookBySymbol("BTC-USD");
        assertOrderBookPrices(orderBook, "50150.00", "50200.00");

        assertDatabaseCounts(4, 2);
    }

    @Test
    @DisplayName("Scenario 4: LIMIT order no match - added to order book")
    void testLimitOrderNoMatch_AddedToBook() {
        // GIVEN: Sell order at higher price
        Order sellOrder = createAndAddToBook(1L, OrderSide.SELL, "50500.00", "1.0");

        // WHEN: Buy order with lower price (no match)
        Order buyOrder = orderService.createOrder(
            OrderTestBuilder.limit()
                .userId(2L)
                .buy()
                .price("50000.00")
                .quantity("1.0")
                .build()
        );

        MatchResult result = matchingEngineService.processOrder(buyOrder);

        // THEN: No match, order added to book
        assertThat(result.isFullyMatched()).isFalse();
        assertThat(result.getTrades()).isEmpty();
        assertThat(result.getModifiedOrders()).isEmpty();

        // Verify incoming order is OPEN
        assertOrderState(result.getUpdatedOrder(), "OPEN", "0.0", "1.0");

        // Verify order book has both orders
        OrderBook orderBook = orderBookService.getOrderBookBySymbol("BTC-USD");
        assertOrderBookPrices(orderBook, "50000.00", "50500.00");
        assertThat(orderBook.getBuyOrders()).hasSize(1);
        assertThat(orderBook.getSellOrders()).hasSize(1);

        // Verify spread
        assertThat(orderBook.getSpread()).isEqualByComparingTo("500.00");

        assertDatabaseCounts(2, 0);
    }

    @Test
    @DisplayName("Scenario 5: LIMIT order respects price-time priority (FIFO)")
    void testLimitOrderPriceTimePriority_FIFO() throws InterruptedException {
        // GIVEN: Multiple sell orders at same price (FIFO queue)
        Order sell1 = createAndAddToBook(1L, OrderSide.SELL, "50000.00", "0.3");
        Thread.sleep(10); // Ensure different timestamps

        Order sell2 = createAndAddToBook(2L, OrderSide.SELL, "50000.00", "0.5");
        Thread.sleep(10);

        Order sell3 = createAndAddToBook(3L, OrderSide.SELL, "50000.00", "0.2");

        // WHEN: Buy order partially fills the queue
        Order buyOrder = orderService.createOrder(
            OrderTestBuilder.limit()
                .userId(4L)
                .buy()
                .price("50000.00")
                .quantity("0.7")
                .build()
        );

        MatchResult result = matchingEngineService.processOrder(buyOrder);

        // THEN: FIFO order respected
        assertThat(result.isFullyMatched()).isTrue();
        assertThat(result.getTrades()).hasSize(2);

        // Verify trades are in FIFO order
        assertTrade(result.getTrades().get(0), buyOrder.getOrderId(), sell1.getOrderId(),
                    "50000.00", "0.3");
        assertTrade(result.getTrades().get(1), buyOrder.getOrderId(), sell2.getOrderId(),
                    "50000.00", "0.4");

        // Verify order book still has sell2 (0.1) and sell3 (0.2)
        OrderBook orderBook = orderBookService.getOrderBookBySymbol("BTC-USD");
        List<Order> remainingOrders = List.copyOf(
            orderBook.getSellOrders().get(new BigDecimal("50000.00"))
        );
        assertThat(remainingOrders).hasSize(2);
        assertThat(remainingOrders.get(0).getUserId()).isEqualTo(2L); // sell2 first
        assertThat(remainingOrders.get(0).getRemainingQuantity()).isEqualByComparingTo("0.1");

        assertDatabaseCounts(4, 2);
    }

    @Test
    @DisplayName("Scenario 6: MARKET order complete fill across price levels")
    void testMarketOrderCompleteFill() {
        // GIVEN: Sufficient liquidity in book
        Order sell1 = createAndAddToBook(1L, OrderSide.SELL, "50000.00", "0.5");
        Order sell2 = createAndAddToBook(2L, OrderSide.SELL, "50100.00", "0.7");

        // WHEN: Market buy order
        Order marketBuy = orderService.createOrder(
            OrderTestBuilder.market()
                .userId(3L)
                .buy()
                .quantity("1.0")
                .build()
        );

        MatchResult result = matchingEngineService.processOrder(marketBuy);

        // THEN: Complete fill at best available prices
        assertThat(result.isFullyMatched()).isTrue();
        assertThat(result.getTrades()).hasSize(2);

        // Verify trades executed at maker prices
        assertTrade(result.getTrades().get(0), marketBuy.getOrderId(), sell1.getOrderId(),
                    "50000.00", "0.5");
        assertTrade(result.getTrades().get(1), marketBuy.getOrderId(), sell2.getOrderId(),
                    "50100.00", "0.5");

        assertOrderState(result.getUpdatedOrder(), "FILLED", "1.0", "0.0");

        assertDatabaseCounts(3, 2);
    }

    @Test
    @DisplayName("Scenario 7: MARKET order insufficient liquidity - partial fill")
    void testMarketOrderInsufficientLiquidity_PartialFill() {
        // GIVEN: Insufficient liquidity
        Order sellOrder = createAndAddToBook(1L, OrderSide.SELL, "50000.00", "0.5");

        // WHEN: Market buy order larger than available liquidity
        Order marketBuy = orderService.createOrder(
            OrderTestBuilder.market()
                .userId(2L)
                .buy()
                .quantity("1.0")
                .build()
        );

        MatchResult result = matchingEngineService.processOrder(marketBuy);

        // THEN: Partial fill, remainder cancelled (not added to book)
        assertThat(result.isFullyMatched()).isFalse();
        assertThat(result.getTrades()).hasSize(1);

        // Verify market order is PARTIALLY_FILLED (not added to book)
        assertOrderState(result.getUpdatedOrder(), "PARTIALLY_FILLED", "0.5", "0.5");

        // Verify market order is NOT in the book
        OrderBook orderBook = orderBookService.getOrderBookBySymbol("BTC-USD");
        assertThat(orderBook.getBuyOrders()).isEmpty();

        assertDatabaseCounts(2, 1);
    }

    @Test
    @DisplayName("Scenario 8: Order cancellation - remove from book")
    void testOrderCancellation_RemoveFromBook() {
        // GIVEN: Open order in book
        Order buyOrder = createAndAddToBook(1L, OrderSide.BUY, "50000.00", "1.0");

        // WHEN: Cancel order
        Order cancelledOrder = orderService.cancelOrder(buyOrder.getOrderId(), 1L);

        // THEN: Order cancelled and removed from book
        assertThat(cancelledOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        // Verify order removed from book
        OrderBook orderBook = orderBookService.getOrderBookBySymbol("BTC-USD");
        assertThat(orderBook.getBuyOrders()).isEmpty();

        // Verify database
        Order dbOrder = orderMapper.findById(buyOrder.getOrderId());
        assertThat(dbOrder.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        assertDatabaseCounts(1, 0);
    }
}
