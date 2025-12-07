package cex.crypto.trading.service;

import cex.crypto.trading.domain.UserBalance;
import cex.crypto.trading.exception.InsufficientBalanceException;
import cex.crypto.trading.mapper.UserBalanceMapper;
import cex.crypto.trading.service.cache.CacheWarmerService;
import cex.crypto.trading.service.cache.DistributedLockService;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * User balance service with multi-level caching
 * L1: Caffeine (1s TTL)
 * L2: Redis (5min TTL)
 * L3: MySQL (source of truth)
 */
@Service
@Slf4j
public class UserBalanceService {

    @Autowired
    private UserBalanceMapper balanceMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private DistributedLockService lockService;

    @Autowired
    private CacheWarmerService cacheWarmer;

    @Value("${cache.balance.local.ttl.seconds:1}")
    private int localTtlSeconds;

    @Value("${cache.balance.redis.ttl.minutes:5}")
    private int redisTtlMinutes;

    // L1 Cache: Caffeine (local, random TTL with jitter)
    private Cache<String, UserBalance> localCache;

    /**
     * Initialize local cache with dynamic expiry after dependencies are injected
     */
    @jakarta.annotation.PostConstruct
    public void initializeCache() {
        localCache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfter(new Expiry<String, UserBalance>() {
                    @Override
                    public long expireAfterCreate(String key, UserBalance value, long currentTime) {
                        return TimeUnit.SECONDS.toNanos(cacheWarmer.getRandomLocalTtl());
                    }

                    @Override
                    public long expireAfterUpdate(String key, UserBalance value, long currentTime, long currentDuration) {
                        return TimeUnit.SECONDS.toNanos(cacheWarmer.getRandomLocalTtl());
                    }

                    @Override
                    public long expireAfterRead(String key, UserBalance value, long currentTime, long currentDuration) {
                        return currentDuration; // No change on read
                    }
                })
                .recordStats()
                .build();
    }

    /**
     * Get balance (Read Path with cache protection: L1 → L2 → L3)
     * - L1: Caffeine cache with random TTL
     * - L2: Redis cache with random TTL
     * - L3: MySQL with distributed lock protection
     */
    public UserBalance getBalance(Long userId, String currency) {
        String cacheKey = buildCacheKey(userId, currency);

        // L1: Check Caffeine cache
        UserBalance balance = localCache.getIfPresent(cacheKey);
        if (balance != null) {
            log.debug("L1 cache hit: {}", cacheKey);
            return balance;
        }

        // L2: Check Redis cache
        balance = (UserBalance) redisTemplate.opsForValue().get(cacheKey);
        if (balance != null) {
            log.debug("L2 cache hit: {}", cacheKey);
            localCache.put(cacheKey, balance);
            return balance;
        }

        // L3: Database query with distributed lock (prevents cache breakdown)
        String lockKey = lockService.buildBalanceLockKey(userId, currency);
        return lockService.executeWithLock(lockKey, () -> {
            // Double-check Redis after acquiring lock
            UserBalance balanceAfterLock = (UserBalance) redisTemplate.opsForValue().get(cacheKey);
            if (balanceAfterLock != null) {
                log.debug("L2 cache hit after lock: {}", cacheKey);
                localCache.put(cacheKey, balanceAfterLock);
                return balanceAfterLock;
            }

            // Load from database
            UserBalance dbBalance = balanceMapper.findByUserIdAndCurrency(userId, currency);
            if (dbBalance == null) {
                // Create initial balance if not exists
                dbBalance = UserBalance.builder()
                        .userId(userId)
                        .currency(currency)
                        .availableBalance(BigDecimal.ZERO)
                        .frozenBalance(BigDecimal.ZERO)
                        .build();
                balanceMapper.insert(dbBalance);
                log.info("Created initial balance: userId={}, currency={}", userId, currency);
            }

            // Update caches with random TTL (prevents cache avalanche)
            long randomRedisTtl = cacheWarmer.getRandomRedisTtl();
            redisTemplate.opsForValue().set(cacheKey, dbBalance, randomRedisTtl, TimeUnit.SECONDS);
            localCache.put(cacheKey, dbBalance);

            log.debug("L3 cache miss, loaded from DB with random TTL: {} seconds", randomRedisTtl);
            return dbBalance;
        });
    }

    /**
     * Update available balance (Write-Through: L3 → L2 → L1 with distributed lock)
     */
    @Transactional
    public UserBalance updateAvailableBalance(Long userId, String currency, BigDecimal amount) {
        String cacheKey = buildCacheKey(userId, currency);
        String lockKey = lockService.buildBalanceLockKey(userId, currency);

        return lockService.executeWithLock(lockKey, () -> {
            // Validate sufficient balance for deduction
            if (amount.compareTo(BigDecimal.ZERO) < 0) {
                UserBalance current = getBalance(userId, currency);
                if (current.getAvailableBalance().add(amount).compareTo(BigDecimal.ZERO) < 0) {
                    throw new InsufficientBalanceException(
                        "Insufficient balance: userId=" + userId +
                        ", currency=" + currency +
                        ", available=" + current.getAvailableBalance() +
                        ", required=" + amount.abs()
                    );
                }
            }

            // L3: Update database
            balanceMapper.updateAvailableBalance(userId, currency, amount);
            UserBalance updated = balanceMapper.findByUserIdAndCurrency(userId, currency);
            log.info("Updated available balance: userId={}, currency={}, amount={}, newBalance={}",
                     userId, currency, amount, updated.getAvailableBalance());

            // L2: Update Redis with random TTL (Write-Through)
            long randomRedisTtl = cacheWarmer.getRandomRedisTtl();
            redisTemplate.opsForValue().set(cacheKey, updated, randomRedisTtl, TimeUnit.SECONDS);

            // L1: Update Caffeine (Write-Through)
            localCache.put(cacheKey, updated);

            return updated;
        });
    }

    /**
     * Update frozen balance (with distributed lock and random TTL)
     */
    @Transactional
    public UserBalance updateFrozenBalance(Long userId, String currency, BigDecimal amount) {
        String cacheKey = buildCacheKey(userId, currency);
        String lockKey = lockService.buildBalanceLockKey(userId, currency);

        return lockService.executeWithLock(lockKey, () -> {
            // L3: Update database
            balanceMapper.updateFrozenBalance(userId, currency, amount);
            UserBalance updated = balanceMapper.findByUserIdAndCurrency(userId, currency);
            log.info("Updated frozen balance: userId={}, currency={}, amount={}, newBalance={}",
                     userId, currency, amount, updated.getFrozenBalance());

            // L2: Update Redis with random TTL (Write-Through)
            long randomRedisTtl = cacheWarmer.getRandomRedisTtl();
            redisTemplate.opsForValue().set(cacheKey, updated, randomRedisTtl, TimeUnit.SECONDS);

            // L1: Update Caffeine (Write-Through)
            localCache.put(cacheKey, updated);

            return updated;
        });
    }

    /**
     * Get all balances for a user
     */
    public List<UserBalance> getUserBalances(Long userId) {
        return balanceMapper.findByUserId(userId);
    }

    /**
     * Invalidate cache for a user's currency balance
     */
    public void invalidateCache(Long userId, String currency) {
        String cacheKey = buildCacheKey(userId, currency);
        localCache.invalidate(cacheKey);
        redisTemplate.delete(cacheKey);
        log.debug("Cache invalidated: {}", cacheKey);
    }

    /**
     * Build cache key
     */
    private String buildCacheKey(Long userId, String currency) {
        return String.format("user:balance:%d:%s", userId, currency);
    }

    /**
     * Get cache statistics
     */
    public String getCacheStats() {
        return localCache.stats().toString();
    }

    /**
     * Warm cache entry (called by CacheWarmerService)
     * @param balance the balance to warm into cache
     */
    public void warmCache(UserBalance balance) {
        if (balance == null) {
            return;
        }

        String cacheKey = buildCacheKey(balance.getUserId(), balance.getCurrency());
        long randomRedisTtl = cacheWarmer.getRandomRedisTtl();

        // Warm both L1 and L2 caches
        redisTemplate.opsForValue().set(cacheKey, balance, randomRedisTtl, TimeUnit.SECONDS);
        localCache.put(cacheKey, balance);

        log.debug("Cache warmed: {}, TTL: {}s", cacheKey, randomRedisTtl);
    }
}
