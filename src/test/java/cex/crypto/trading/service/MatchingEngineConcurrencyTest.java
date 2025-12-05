package cex.crypto.trading.service;

import cex.crypto.trading.BaseIntegrationTest;
import cex.crypto.trading.domain.Order;
import cex.crypto.trading.domain.OrderBook;
import cex.crypto.trading.dto.MatchResult;
import cex.crypto.trading.enums.OrderSide;
import cex.crypto.trading.testutil.OrderTestBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Concurrency tests for MatchingEngineService
 * Tests thread safety and optimistic locking
 */
@DisplayName("Matching Engine Concurrency Tests")
class MatchingEngineConcurrencyTest extends BaseIntegrationTest {

    @Autowired
    private MatchingEngineService matchingEngineService;

    @Test
    @DisplayName("Concurrent orders to same symbol - synchronized access")
    void testConcurrentOrdersToSameSymbol() throws InterruptedException {
        // GIVEN: Initial sell orders in book
        IntStream.range(0, 10).forEach(i -> {
            createAndAddToBook((long) i, OrderSide.SELL, "50000.00", "0.1");
        });

        // WHEN: Process 10 concurrent buy orders
        int numThreads = 10;
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch completionLatch = new CountDownLatch(numThreads);

        List<Future<MatchResult>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            final int userId = 100 + i;
            Future<MatchResult> future = executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for signal to start

                    Order buyOrder = orderService.createOrder(
                        OrderTestBuilder.limit()
                            .userId((long) userId)
                            .buy()
                            .price("50000.00")
                            .quantity("0.1")
                            .build()
                    );

                    return matchingEngineService.processOrder(buyOrder);
                } finally {
                    completionLatch.countDown();
                }
            });
            futures.add(future);
        }

        // Start all threads at once
        startLatch.countDown();

        // Wait for all threads to complete (with timeout)
        boolean completed = completionLatch.await(30, TimeUnit.SECONDS);
        assertThat(completed).isTrue();

        executor.shutdown();

        // THEN: Verify all orders processed correctly
        int successCount = 0;
        int totalTrades = 0;

        for (Future<MatchResult> future : futures) {
            try {
                MatchResult result = future.get();
                if (result.isFullyMatched()) {
                    successCount++;
                }
                totalTrades += result.getTrades().size();
            } catch (ExecutionException e) {
                System.out.println("Order failed: " + e.getCause().getMessage());
            }
        }

        assertThat(successCount).isEqualTo(10);
        assertThat(totalTrades).isEqualTo(10);

        // Verify final order book state (should be empty)
        OrderBook orderBook = orderBookService.getOrderBookBySymbol("BTC-USD");
        assertThat(orderBook.getBuyOrders()).isEmpty();
        assertThat(orderBook.getSellOrders()).isEmpty();
    }

    @Test
    @DisplayName("Optimistic locking conflict - retry mechanism")
    void testOptimisticLockingConflict_Retry() throws InterruptedException, ExecutionException, TimeoutException {
        // GIVEN: Initial order book with sell orders
        createAndAddToBook(1L, OrderSide.SELL, "50000.00", "1.0");
        createAndAddToBook(2L, OrderSide.SELL, "50100.00", "1.0");

        OrderBook orderBook = orderBookService.getOrderBookBySymbol("BTC-USD");
        Integer initialVersion = orderBook.getVersion();

        // WHEN: Trigger concurrent updates
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Callable<MatchResult> task1 = () -> {
            startLatch.await();
            Order buyOrder = orderService.createOrder(
                OrderTestBuilder.limit()
                    .userId(10L)
                    .buy()
                    .price("50000.00")
                    .quantity("0.5")
                    .build()
            );
            return matchingEngineService.processOrder(buyOrder);
        };

        Callable<MatchResult> task2 = () -> {
            startLatch.await();
            Order buyOrder = orderService.createOrder(
                OrderTestBuilder.limit()
                    .userId(11L)
                    .buy()
                    .price("50100.00")
                    .quantity("0.5")
                    .build()
            );
            return matchingEngineService.processOrder(buyOrder);
        };

        Future<MatchResult> future1 = executor.submit(task1);
        Future<MatchResult> future2 = executor.submit(task2);

        startLatch.countDown(); // Start both tasks

        // THEN: Both should succeed (retry mechanism handles conflicts)
        MatchResult result1 = future1.get(10, TimeUnit.SECONDS);
        MatchResult result2 = future2.get(10, TimeUnit.SECONDS);

        assertThat(result1.isFullyMatched()).isTrue();
        assertThat(result2.isFullyMatched()).isTrue();

        executor.shutdown();

        // Verify version incremented
        OrderBook finalOrderBook = orderBookService.getOrderBookBySymbol("BTC-USD");
        assertThat(finalOrderBook.getVersion()).isGreaterThan(initialVersion);
    }
}
