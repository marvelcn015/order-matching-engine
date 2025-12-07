package cex.crypto.trading.service.cache;

import cex.crypto.trading.dto.BloomFilterStats;
import cex.crypto.trading.mapper.OrderMapper;
import cex.crypto.trading.mapper.UserMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Bloom Filter service for cache penetration protection
 * Uses Redisson RBloomFilter to block queries for non-existent entities
 */
@Service
@Slf4j
public class BloomFilterService {

    @Autowired
    private RedissonClient redissonClient;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Value("${bloom.filter.user.expected.insertions:1000000}")
    private long userExpectedInsertions;

    @Value("${bloom.filter.user.false.positive.probability:0.01}")
    private double userFpp;

    @Value("${bloom.filter.order.expected.insertions:10000000}")
    private long orderExpectedInsertions;

    @Value("${bloom.filter.order.false.positive.probability:0.01}")
    private double orderFpp;

    @Value("${bloom.filter.initialization.batch.size:10000}")
    private int batchSize;

    private RBloomFilter<Long> userBloomFilter;
    private RBloomFilter<Long> orderBloomFilter;

    /**
     * Initialize Bloom Filters on application startup
     * Only initializes the Bloom Filter structure, does not load data
     */
    @PostConstruct
    public void initialize() {
        log.info("Initializing Bloom Filters...");

        // Initialize user Bloom Filter
        userBloomFilter = redissonClient.getBloomFilter("bloom:filter:users");
        if (!userBloomFilter.isExists()) {
            userBloomFilter.tryInit(userExpectedInsertions, userFpp);
            log.info("User Bloom Filter initialized: capacity={}, fpp={}",
                    userExpectedInsertions, userFpp);
        } else {
            log.info("User Bloom Filter already exists, skipping initialization");
        }

        // Initialize order Bloom Filter
        orderBloomFilter = redissonClient.getBloomFilter("bloom:filter:orders");
        if (!orderBloomFilter.isExists()) {
            orderBloomFilter.tryInit(orderExpectedInsertions, orderFpp);
            log.info("Order Bloom Filter initialized: capacity={}, fpp={}",
                    orderExpectedInsertions, orderFpp);
        } else {
            log.info("Order Bloom Filter already exists, skipping initialization");
        }

        log.info("Bloom Filter initialization completed");
    }

    /**
     * Load existing data into Bloom Filters after application is fully started
     * Uses ApplicationReadyEvent to ensure database is ready
     */
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void loadExistingDataOnReady() {
        log.info("Loading existing data into Bloom Filters...");

        // Load existing data asynchronously to avoid blocking
        CompletableFuture.runAsync(this::loadExistingUsers)
                .exceptionally(e -> {
                    log.error("Failed to load existing users into Bloom Filter", e);
                    return null;
                });

        CompletableFuture.runAsync(this::loadExistingOrders)
                .exceptionally(e -> {
                    log.error("Failed to load existing orders into Bloom Filter", e);
                    return null;
                });
    }

    /**
     * Load existing users from database into Bloom Filter
     */
    private void loadExistingUsers() {
        log.info("Loading existing users into Bloom Filter...");
        long startTime = System.currentTimeMillis();
        long totalCount = 0;
        long offset = 0;

        while (true) {
            List<Long> userIds = userMapper.findAllUserIds(offset, batchSize);
            if (userIds == null || userIds.isEmpty()) {
                break;
            }

            for (Long userId : userIds) {
                userBloomFilter.add(userId);
            }

            totalCount += userIds.size();
            offset += batchSize;

            if (userIds.size() < batchSize) {
                break; // Last batch
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Loaded {} users into Bloom Filter in {}ms", totalCount, duration);
    }

    /**
     * Load existing orders from database into Bloom Filter
     */
    private void loadExistingOrders() {
        log.info("Loading existing orders into Bloom Filter...");
        long startTime = System.currentTimeMillis();
        long totalCount = 0;
        long offset = 0;

        while (true) {
            List<Long> orderIds = orderMapper.findAllOrderIds(offset, batchSize);
            if (orderIds == null || orderIds.isEmpty()) {
                break;
            }

            for (Long orderId : orderIds) {
                orderBloomFilter.add(orderId);
            }

            totalCount += orderIds.size();
            offset += batchSize;

            if (orderIds.size() < batchSize) {
                break; // Last batch
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        log.info("Loaded {} orders into Bloom Filter in {}ms", totalCount, duration);
    }

    /**
     * Check if user may exist in the system
     * @param userId the user ID to check
     * @return true if user may exist (with small false positive rate), false if definitely does not exist
     */
    public boolean mayExistUser(Long userId) {
        if (userId == null) {
            return false;
        }
        return userBloomFilter.contains(userId);
    }

    /**
     * Check if order may exist in the system
     * @param orderId the order ID to check
     * @return true if order may exist (with small false positive rate), false if definitely does not exist
     */
    public boolean mayExistOrder(Long orderId) {
        if (orderId == null) {
            return false;
        }
        return orderBloomFilter.contains(orderId);
    }

    /**
     * Add user ID to Bloom Filter
     * Should be called when a new user is created
     * @param userId the user ID to add
     */
    public void addUser(Long userId) {
        if (userId != null) {
            userBloomFilter.add(userId);
            log.debug("Added user {} to Bloom Filter", userId);
        }
    }

    /**
     * Add order ID to Bloom Filter
     * Should be called when a new order is created
     * @param orderId the order ID to add
     */
    public void addOrder(Long orderId) {
        if (orderId != null) {
            orderBloomFilter.add(orderId);
            log.debug("Added order {} to Bloom Filter", orderId);
        }
    }

    /**
     * Get Bloom Filter statistics
     * @return statistics object with filter metrics
     */
    public BloomFilterStats getStats() {
        long userCount = userBloomFilter.count();
        long orderCount = orderBloomFilter.count();

        double userUtilization = (double) userCount / userExpectedInsertions * 100;
        double orderUtilization = (double) orderCount / orderExpectedInsertions * 100;

        return BloomFilterStats.builder()
                .userFilterSize(userCount)
                .userFilterCapacity(userExpectedInsertions)
                .userFilterUtilization(userUtilization)
                .orderFilterSize(orderCount)
                .orderFilterCapacity(orderExpectedInsertions)
                .orderFilterUtilization(orderUtilization)
                .build();
    }

    /**
     * Rebuild Bloom Filters from scratch
     * Use this method if filters become corrupted or need to be reset
     */
    public void rebuild() {
        log.warn("Rebuilding Bloom Filters from scratch...");

        // Delete existing filters
        userBloomFilter.delete();
        orderBloomFilter.delete();

        // Reinitialize
        initialize();

        log.info("Bloom Filter rebuild completed");
    }
}
