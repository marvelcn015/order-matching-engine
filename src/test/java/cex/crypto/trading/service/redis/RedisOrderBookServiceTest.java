package cex.crypto.trading.service.redis;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.domain.OrderBook;
import cex.crypto.trading.enums.OrderSide;
import cex.crypto.trading.enums.OrderStatus;
import cex.crypto.trading.enums.OrderType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for RedisOrderBookService.
 *
 * Tests cover:
 * - Save and load order book
 * - Buy order descending order (highest price first)
 * - Sell order ascending order (lowest price first)
 * - FIFO order within price level
 * - Empty order book handling
 * - Delete order book
 */
@SpringBootTest
@ActiveProfiles("test")
class RedisOrderBookServiceTest {

    @Autowired
    private RedisOrderBookService redisOrderBookService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * Cleanup Redis after each test.
     */
    @AfterEach
    void cleanupRedis() {
        Set<String> keys = redisTemplate.keys("*");
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    /**
     * Test complete save and load cycle.
     */
    @Test
    void testSaveAndLoadOrderBook() {
        // Given: Order book with buy and sell orders
        OrderBook orderBook = createTestOrderBook("BTC-USD", 5, LocalDateTime.now());

        // Add buy orders at different prices
        addOrderToBook(orderBook, OrderSide.BUY, "51000", 1L, "1.5");
        addOrderToBook(orderBook, OrderSide.BUY, "50000", 2L, "2.0");

        // Add sell orders at different prices
        addOrderToBook(orderBook, OrderSide.SELL, "52000", 3L, "1.0");
        addOrderToBook(orderBook, OrderSide.SELL, "53000", 4L, "0.5");

        // When: Save to Redis
        redisOrderBookService.saveToRedis(orderBook);

        // Then: Load from Redis
        OrderBook loaded = redisOrderBookService.loadFromRedis("BTC-USD");

        // Verify metadata
        assertThat(loaded).isNotNull();
        assertThat(loaded.getSymbol()).isEqualTo("BTC-USD");
        assertThat(loaded.getVersion()).isEqualTo(5);

        // Verify buy orders
        assertThat(loaded.getBuyOrders()).hasSize(2);
        assertThat(loaded.getBestBid()).isEqualByComparingTo("51000");

        // Verify sell orders
        assertThat(loaded.getSellOrders()).hasSize(2);
        assertThat(loaded.getBestAsk()).isEqualByComparingTo("52000");
    }

    /**
     * Test buy orders are sorted in descending order (highest price first).
     */
    @Test
    void testBuyOrderDescendingOrder() {
        // Given: Order book with buy orders at random prices
        OrderBook orderBook = createTestOrderBook("ETH-USD", 1, LocalDateTime.now());

        addOrderToBook(orderBook, OrderSide.BUY, "3000", 1L, "1.0");
        addOrderToBook(orderBook, OrderSide.BUY, "3100", 2L, "2.0");
        addOrderToBook(orderBook, OrderSide.BUY, "2900", 3L, "1.5");

        // When: Save and load
        redisOrderBookService.saveToRedis(orderBook);
        OrderBook loaded = redisOrderBookService.loadFromRedis("ETH-USD");

        // Then: Verify descending order (highest first)
        List<BigDecimal> prices = new ArrayList<>(loaded.getBuyOrders().keySet());
        assertThat(prices).containsExactly(
                new BigDecimal("3100"),
                new BigDecimal("3000"),
                new BigDecimal("2900")
        );

        // Verify best bid is highest price
        assertThat(loaded.getBestBid()).isEqualByComparingTo("3100");
    }

    /**
     * Test sell orders are sorted in ascending order (lowest price first).
     */
    @Test
    void testSellOrderAscendingOrder() {
        // Given: Order book with sell orders at random prices
        OrderBook orderBook = createTestOrderBook("ETH-USD", 1, LocalDateTime.now());

        addOrderToBook(orderBook, OrderSide.SELL, "3200", 1L, "1.0");
        addOrderToBook(orderBook, OrderSide.SELL, "3100", 2L, "2.0");
        addOrderToBook(orderBook, OrderSide.SELL, "3300", 3L, "1.5");

        // When: Save and load
        redisOrderBookService.saveToRedis(orderBook);
        OrderBook loaded = redisOrderBookService.loadFromRedis("ETH-USD");

        // Then: Verify ascending order (lowest first)
        List<BigDecimal> prices = new ArrayList<>(loaded.getSellOrders().keySet());
        assertThat(prices).containsExactly(
                new BigDecimal("3100"),
                new BigDecimal("3200"),
                new BigDecimal("3300")
        );

        // Verify best ask is lowest price
        assertThat(loaded.getBestAsk()).isEqualByComparingTo("3100");
    }

    /**
     * Test FIFO order is maintained within same price level.
     */
    @Test
    void testFIFOWithinPriceLevel() {
        // Given: Order book with multiple orders at same price
        OrderBook orderBook = createTestOrderBook("BTC-USD", 1, LocalDateTime.now());

        // Add 3 buy orders at same price (order matters!)
        addOrderToBook(orderBook, OrderSide.BUY, "50000", 1L, "1.0");
        addOrderToBook(orderBook, OrderSide.BUY, "50000", 2L, "2.0");
        addOrderToBook(orderBook, OrderSide.BUY, "50000", 3L, "3.0");

        // When: Save and load
        redisOrderBookService.saveToRedis(orderBook);
        OrderBook loaded = redisOrderBookService.loadFromRedis("BTC-USD");

        // Then: Verify FIFO order (order IDs should be 1, 2, 3)
        ConcurrentLinkedQueue<Order> ordersAtPrice = loaded.getBuyOrders().get(new BigDecimal("50000"));
        assertThat(ordersAtPrice).hasSize(3);

        List<Long> orderIds = ordersAtPrice.stream()
                .map(Order::getOrderId)
                .toList();

        assertThat(orderIds).containsExactly(1L, 2L, 3L);

        // Verify quantities match insertion order
        List<BigDecimal> quantities = ordersAtPrice.stream()
                .map(Order::getQuantity)
                .toList();

        assertThat(quantities).containsExactly(
                new BigDecimal("1.0"),
                new BigDecimal("2.0"),
                new BigDecimal("3.0")
        );
    }

    /**
     * Test empty order book handling.
     */
    @Test
    void testEmptyOrderBook() {
        // Given: Order book with no orders
        OrderBook orderBook = createTestOrderBook("EMPTY-USD", 0, LocalDateTime.now());

        // When: Save and load
        redisOrderBookService.saveToRedis(orderBook);
        OrderBook loaded = redisOrderBookService.loadFromRedis("EMPTY-USD");

        // Then: Verify empty but valid order book
        assertThat(loaded).isNotNull();
        assertThat(loaded.getSymbol()).isEqualTo("EMPTY-USD");
        assertThat(loaded.getBuyOrders()).isEmpty();
        assertThat(loaded.getSellOrders()).isEmpty();
        assertThat(loaded.getBestBid()).isNull();
        assertThat(loaded.getBestAsk()).isNull();
    }

    /**
     * Test delete order book.
     */
    @Test
    void testDeleteOrderBook() {
        // Given: Order book with orders
        OrderBook orderBook = createTestOrderBook("BTC-USD", 1, LocalDateTime.now());
        addOrderToBook(orderBook, OrderSide.BUY, "50000", 1L, "1.0");

        redisOrderBookService.saveToRedis(orderBook);

        // Verify it exists
        OrderBook loaded = redisOrderBookService.loadFromRedis("BTC-USD");
        assertThat(loaded).isNotNull();

        // When: Delete
        redisOrderBookService.deleteFromRedis("BTC-USD");

        // Then: Should not exist
        OrderBook afterDelete = redisOrderBookService.loadFromRedis("BTC-USD");
        assertThat(afterDelete).isNull();
    }

    /**
     * Test loading non-existent order book returns null.
     */
    @Test
    void testLoadNonExistentOrderBook() {
        // When: Load non-existent order book
        OrderBook loaded = redisOrderBookService.loadFromRedis("NON-EXISTENT");

        // Then: Should return null
        assertThat(loaded).isNull();
    }

    /**
     * Test overwriting existing order book.
     */
    @Test
    void testOverwriteOrderBook() {
        // Given: Initial order book
        OrderBook orderBook1 = createTestOrderBook("BTC-USD", 1, LocalDateTime.now());
        addOrderToBook(orderBook1, OrderSide.BUY, "50000", 1L, "1.0");
        redisOrderBookService.saveToRedis(orderBook1);

        // When: Overwrite with new version
        OrderBook orderBook2 = createTestOrderBook("BTC-USD", 2, LocalDateTime.now().plusSeconds(10));
        addOrderToBook(orderBook2, OrderSide.BUY, "51000", 2L, "2.0");
        redisOrderBookService.saveToRedis(orderBook2);

        // Then: Should have new version
        OrderBook loaded = redisOrderBookService.loadFromRedis("BTC-USD");
        assertThat(loaded.getVersion()).isEqualTo(2);
        assertThat(loaded.getBestBid()).isEqualByComparingTo("51000");
        assertThat(loaded.getBuyOrders()).hasSize(1); // Old order replaced
    }

    // ============= HELPER METHODS =============

    /**
     * Create test order book.
     */
    private OrderBook createTestOrderBook(String symbol, Integer version, LocalDateTime updatedAt) {
        return OrderBook.builder()
                .symbol(symbol)
                .version(version)
                .updatedAt(updatedAt)
                .build();
    }

    /**
     * Add order to order book.
     */
    private void addOrderToBook(OrderBook orderBook, OrderSide side, String price, Long orderId, String quantity) {
        Order order = Order.builder()
                .orderId(orderId)
                .userId(100L)
                .symbol(orderBook.getSymbol())
                .side(side)
                .type(OrderType.LIMIT)
                .price(new BigDecimal(price))
                .quantity(new BigDecimal(quantity))
                .filledQuantity(BigDecimal.ZERO)
                .status(OrderStatus.OPEN)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        if (side == OrderSide.BUY) {
            orderBook.getBuyOrders()
                    .computeIfAbsent(order.getPrice(), k -> new ConcurrentLinkedQueue<>())
                    .offer(order);
        } else {
            orderBook.getSellOrders()
                    .computeIfAbsent(order.getPrice(), k -> new ConcurrentLinkedQueue<>())
                    .offer(order);
        }
    }
}
