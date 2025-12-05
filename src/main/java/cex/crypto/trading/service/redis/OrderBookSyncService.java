package cex.crypto.trading.service.redis;

import cex.crypto.trading.domain.OrderBook;
import cex.crypto.trading.mapper.OrderBookMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for synchronizing order books between MySQL and Redis.
 *
 * Responsibilities:
 * 1. Scheduled sync: Every 5 seconds, sync active order books from MySQL to Redis
 * 2. Symbol registration: Track which symbols need periodic sync
 * 3. Startup recovery: Compare Redis vs MySQL and use newer version
 * 4. Error handling: Graceful degradation if Redis unavailable
 */
@Slf4j
@Service
public class OrderBookSyncService {

    @Autowired
    private OrderBookMapper orderBookMapper;

    @Autowired
    private RedisOrderBookService redisOrderBookService;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    // Track active symbols for periodic sync
    private final Set<String> activeSymbols = ConcurrentHashMap.newKeySet();

    /**
     * Scheduled task: Sync all active order books to Redis every 5 seconds.
     * Initial delay of 10 seconds allows system warmup.
     */
    @Scheduled(fixedRate = 5000, initialDelay = 10000)
    public void syncOrderBooksToRedis() {
        if (activeSymbols.isEmpty()) {
            log.debug("No active symbols to sync");
            return;
        }

        // Check Redis availability first
        if (!isRedisAvailable()) {
            log.warn("Redis unavailable, skipping sync cycle");
            return;
        }

        log.debug("Starting scheduled Redis sync for {} symbols", activeSymbols.size());

        int successCount = 0;
        int errorCount = 0;

        for (String symbol : activeSymbols) {
            try {
                // Load latest from MySQL
                OrderBook orderBook = orderBookMapper.findBySymbol(symbol);

                if (orderBook == null) {
                    log.warn("Order book not found in MySQL during sync: {}", symbol);
                    continue;
                }

                // Save to Redis
                redisOrderBookService.saveToRedis(orderBook);
                successCount++;

            } catch (Exception e) {
                log.error("Error syncing order book to Redis: symbol={}, error={}",
                        symbol, e.getMessage(), e);
                errorCount++;
            }
        }

        if (successCount > 0 || errorCount > 0) {
            log.info("Redis sync complete: success={}, errors={}, activeSymbols={}",
                    successCount, errorCount, activeSymbols.size());
        }
    }

    /**
     * Register symbol for periodic sync.
     * Called by MatchingEngineService after processing first order for a symbol.
     * Idempotent - safe to call multiple times.
     *
     * @param symbol Trading symbol to register
     */
    public void registerSymbol(String symbol) {
        boolean isNew = activeSymbols.add(symbol);
        if (isNew) {
            log.info("Registered symbol for Redis sync: {}", symbol);
        }
    }

    /**
     * Unregister symbol from periodic sync (optional - for cleanup).
     *
     * @param symbol Trading symbol to unregister
     */
    public void unregisterSymbol(String symbol) {
        boolean removed = activeSymbols.remove(symbol);
        if (removed) {
            log.info("Unregistered symbol from Redis sync: {}", symbol);
        }
    }

    /**
     * Startup recovery: Compare Redis vs MySQL and load newer version.
     * Called by OrderBookRecoveryRunner on application startup.
     *
     * @param symbol Trading symbol to recover
     */
    public void recoverOrderBookOnStartup(String symbol) {
        log.info("Starting order book recovery for symbol: {}", symbol);

        try {
            // 1. Load from both sources
            OrderBook mysqlOrderBook = orderBookMapper.findBySymbol(symbol);
            OrderBook redisOrderBook = redisOrderBookService.loadFromRedis(symbol);

            // 2. Determine which is newer
            OrderBook newerOrderBook = selectNewerVersion(mysqlOrderBook, redisOrderBook, symbol);

            if (newerOrderBook == null) {
                log.warn("Both MySQL and Redis have no data for symbol: {}", symbol);
                return;
            }

            // 3. Sync to the older source
            if (newerOrderBook == mysqlOrderBook) {
                log.info("MySQL is newer (version={}), syncing to Redis",
                        mysqlOrderBook.getVersion());
                redisOrderBookService.saveToRedis(mysqlOrderBook);
            } else {
                log.info("Redis is newer (version={}), syncing to MySQL",
                        redisOrderBook.getVersion());
                syncRedisToMySQL(redisOrderBook);
            }

            // 4. Register for periodic sync
            registerSymbol(symbol);

            log.info("Order book recovery complete for symbol: {}", symbol);

        } catch (Exception e) {
            log.error("Error during order book recovery: symbol={}, error={}",
                    symbol, e.getMessage(), e);
            // Fallback: Continue with MySQL data
        }
    }

    /**
     * Select the newer version between MySQL and Redis.
     *
     * @param mysqlOrderBook Order book from MySQL
     * @param redisOrderBook Order book from Redis
     * @param symbol Trading symbol
     * @return Newer order book or null if both are null
     */
    private OrderBook selectNewerVersion(
            OrderBook mysqlOrderBook,
            OrderBook redisOrderBook,
            String symbol) {

        if (mysqlOrderBook == null && redisOrderBook == null) {
            return null;
        }

        if (mysqlOrderBook == null) {
            log.warn("MySQL has no data for {}, using Redis (version={})",
                    symbol, redisOrderBook.getVersion());
            return redisOrderBook;
        }

        if (redisOrderBook == null) {
            log.info("Redis has no data for {}, using MySQL (version={})",
                    symbol, mysqlOrderBook.getVersion());
            return mysqlOrderBook;
        }

        // Compare versions
        int mysqlVersion = mysqlOrderBook.getVersion();
        int redisVersion = redisOrderBook.getVersion();

        if (mysqlVersion > redisVersion) {
            log.info("MySQL version ({}) > Redis version ({})",
                    mysqlVersion, redisVersion);
            return mysqlOrderBook;
        } else if (redisVersion > mysqlVersion) {
            log.info("Redis version ({}) > MySQL version ({})",
                    redisVersion, mysqlVersion);
            return redisOrderBook;
        } else {
            // Versions equal - use updatedAt timestamp as tiebreaker
            LocalDateTime mysqlTime = mysqlOrderBook.getUpdatedAt();
            LocalDateTime redisTime = redisOrderBook.getUpdatedAt();

            if (mysqlTime.isAfter(redisTime)) {
                log.info("Versions equal ({}), MySQL timestamp is newer", mysqlVersion);
                return mysqlOrderBook;
            } else {
                log.info("Versions equal ({}), Redis timestamp is newer or equal", redisVersion);
                return redisOrderBook;
            }
        }
    }

    /**
     * Sync Redis order book back to MySQL.
     * Used during startup recovery when Redis is newer.
     *
     * @param redisOrderBook Order book from Redis
     */
    private void syncRedisToMySQL(OrderBook redisOrderBook) {
        try {
            // Check if exists in MySQL
            OrderBook existing = orderBookMapper.findBySymbol(redisOrderBook.getSymbol());

            if (existing == null) {
                // Insert new
                orderBookMapper.insert(redisOrderBook);
                log.info("Inserted Redis order book into MySQL: symbol={}",
                        redisOrderBook.getSymbol());
            } else {
                // Update existing
                // Set ID to maintain MySQL primary key
                redisOrderBook.setId(existing.getId());

                // Force update by setting version to match existing
                // This bypasses optimistic locking check
                int currentVersion = existing.getVersion();
                redisOrderBook.setVersion(currentVersion);

                orderBookMapper.update(redisOrderBook);

                log.info("Updated MySQL order book from Redis: symbol={}, version={}",
                        redisOrderBook.getSymbol(), redisOrderBook.getVersion() + 1);
            }
        } catch (Exception e) {
            log.error("Error syncing Redis to MySQL: symbol={}, error={}",
                    redisOrderBook.getSymbol(), e.getMessage(), e);
            throw e;
        }
    }

    /**
     * Check if Redis is available.
     *
     * @return true if Redis connection is healthy
     */
    public boolean isRedisAvailable() {
        try {
            redisTemplate.getConnectionFactory().getConnection().ping();
            return true;
        } catch (Exception e) {
            log.warn("Redis connection check failed: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Get count of active symbols being synced.
     *
     * @return Number of active symbols
     */
    public int getActiveSymbolCount() {
        return activeSymbols.size();
    }

    /**
     * Get all active symbols (for monitoring/debugging).
     *
     * @return Set of active symbols
     */
    public Set<String> getActiveSymbols() {
        return Set.copyOf(activeSymbols);
    }
}
