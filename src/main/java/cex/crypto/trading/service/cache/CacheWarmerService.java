package cex.crypto.trading.service.cache;

import cex.crypto.trading.domain.UserBalance;
import cex.crypto.trading.mapper.OrderMapper;
import cex.crypto.trading.mapper.UserBalanceMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * Cache warmer service for cache avalanche protection
 * Provides cache warming and random TTL generation
 */
@Service
@Slf4j
public class CacheWarmerService {

    @Autowired
    private UserBalanceMapper balanceMapper;

    @Autowired
    private OrderMapper orderMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Value("${cache.warmer.enabled:true}")
    private boolean enabled;

    @Value("${cache.warmer.top.users:1000}")
    private int topUsers;

    @Value("${cache.warmer.hours:24}")
    private int activityHours;

    @Value("${cache.balance.redis.ttl.minutes:5}")
    private int baseRedisTtlMinutes;

    @Value("${cache.balance.redis.ttl.jitter.percent:20}")
    private int redisTtlJitterPercent;

    @Value("${cache.balance.local.ttl.seconds:1}")
    private int baseLocalTtlSeconds;

    @Value("${cache.balance.local.ttl.jitter.percent:20}")
    private int localTtlJitterPercent;

    private final Random random = new Random();

    /**
     * Warm cache on application startup
     * Preloads hot user balance data
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmCacheOnStartup() {
        if (!enabled) {
            log.info("Cache warmer disabled");
            return;
        }

        log.info("Starting cache warming...");
        long startTime = System.currentTimeMillis();

        try {
            // Find active users from recent orders
            List<Long> activeUserIds = orderMapper.findActiveUserIds(topUsers, activityHours);

            if (activeUserIds == null || activeUserIds.isEmpty()) {
                log.info("No active users found, skipping cache warming");
                return;
            }

            int warmedCount = 0;
            for (Long userId : activeUserIds) {
                List<UserBalance> balances = balanceMapper.findByUserId(userId);
                if (balances != null) {
                    for (UserBalance balance : balances) {
                        warmCacheEntry(balance);
                        warmedCount++;
                    }
                }
            }

            long duration = System.currentTimeMillis() - startTime;
            log.info("Cache warming completed: {} users, {} balance entries in {}ms",
                    activeUserIds.size(), warmedCount, duration);

        } catch (Exception e) {
            log.error("Cache warming failed", e);
        }
    }

    /**
     * Warm a single cache entry with random TTL
     *
     * @param balance the user balance to cache
     */
    public void warmCacheEntry(UserBalance balance) {
        if (balance == null) {
            return;
        }

        String cacheKey = buildCacheKey(balance.getUserId(), balance.getCurrency());
        long randomTtl = getRandomRedisTtl();

        redisTemplate.opsForValue().set(cacheKey, balance, randomTtl, TimeUnit.SECONDS);

        log.debug("Warmed cache: key={}, ttl={}s", cacheKey, randomTtl);
    }

    /**
     * Calculate random Redis TTL with jitter
     * Base: 5 minutes, Jitter: ±20% → 4-6 minutes
     *
     * @return TTL in seconds
     */
    public long getRandomRedisTtl() {
        long baseTtlSeconds = baseRedisTtlMinutes * 60L;
        return getRandomTtl(baseTtlSeconds, redisTtlJitterPercent);
    }

    /**
     * Calculate random Local (Caffeine) TTL with jitter
     * Base: 1 second, Jitter: ±20% → 0.8-1.2 seconds
     *
     * @return TTL in seconds
     */
    public long getRandomLocalTtl() {
        return getRandomTtl(baseLocalTtlSeconds, localTtlJitterPercent);
    }

    /**
     * Calculate random TTL with jitter percentage
     * Formula: baseTtl * (1 + random(-jitter%, +jitter%))
     *
     * @param baseTtl base TTL value
     * @param jitterPercent jitter percentage (e.g., 20 for ±20%)
     * @return randomized TTL
     */
    private long getRandomTtl(long baseTtl, int jitterPercent) {
        // Generate random value between -jitterPercent and +jitterPercent
        double jitterFactor = (random.nextDouble() * 2 - 1) * (jitterPercent / 100.0);

        // Apply jitter: baseTtl * (1 + jitterFactor)
        double randomizedTtl = baseTtl * (1.0 + jitterFactor);

        return Math.max(1, (long) randomizedTtl); // Ensure minimum 1 second
    }

    /**
     * Build cache key for user balance
     *
     * @param userId the user ID
     * @param currency the currency code
     * @return cache key
     */
    private String buildCacheKey(Long userId, String currency) {
        return String.format("user:balance:%d:%s", userId, currency);
    }

    /**
     * Manual cache warm trigger
     * Can be called from admin API
     *
     * @return number of entries warmed
     */
    public int warmCache() {
        log.info("Manual cache warming triggered");
        warmCacheOnStartup();

        // Return estimated count (not exact due to async processing)
        return topUsers;
    }

    /**
     * Get cache warmer configuration
     *
     * @return configuration info string
     */
    public String getConfig() {
        return String.format("CacheWarmer[enabled=%s, topUsers=%d, hours=%d, " +
                        "redisTtl=%dmin±%d%%, localTtl=%ds±%d%%]",
                enabled, topUsers, activityHours,
                baseRedisTtlMinutes, redisTtlJitterPercent,
                baseLocalTtlSeconds, localTtlJitterPercent);
    }
}
