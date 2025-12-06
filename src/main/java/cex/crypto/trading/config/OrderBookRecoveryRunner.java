package cex.crypto.trading.config;

import cex.crypto.trading.domain.OrderBook;
import cex.crypto.trading.mapper.OrderBookMapper;
import cex.crypto.trading.service.redis.OrderBookSyncService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Application startup runner for order book recovery.
 *
 * On application startup, this component:
 * 1. Checks Redis availability
 * 2. Loads all order books from MySQL
 * 3. Compares each with Redis version (if exists)
 * 4. Syncs to ensure both sources have the newer version
 * 5. Registers symbols for periodic sync
 *
 * Execution order: 1 (runs early in startup sequence)
 */
@Slf4j
@Component
@Order(1)
public class OrderBookRecoveryRunner implements ApplicationRunner {

    @Autowired
    private OrderBookMapper orderBookMapper;

    @Autowired
    private OrderBookSyncService syncService;

    /**
     * Run order book recovery on application startup.
     *
     * @param args Application arguments
     */
    @Override
    public void run(ApplicationArguments args) {
        log.info("=== Starting Order Book Recovery ===");

        // Check Redis availability
        if (!syncService.isRedisAvailable()) {
            log.warn("Redis is not available, skipping recovery. Will use MySQL only.");
            return;
        }

        // Get all symbols from MySQL
        List<OrderBook> allOrderBooks = orderBookMapper.findAll();

        if (allOrderBooks.isEmpty()) {
            log.info("No order books found in MySQL, nothing to recover");
            return;
        }

        log.info("Found {} order books in MySQL, starting recovery", allOrderBooks.size());

        int successCount = 0;
        int errorCount = 0;

        for (OrderBook orderBook : allOrderBooks) {
            try {
                syncService.recoverOrderBookOnStartup(orderBook.getSymbol());
                successCount++;
            } catch (Exception e) {
                log.error("Failed to recover order book: symbol={}, error={}",
                        orderBook.getSymbol(), e.getMessage(), e);
                errorCount++;
                // Continue with next symbol
            }
        }

        log.info("=== Order Book Recovery Complete: success={}, errors={} ===",
                successCount, errorCount);
    }
}
