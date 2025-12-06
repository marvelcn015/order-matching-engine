package cex.crypto.trading.service;

import cex.crypto.trading.domain.UserBalance;
import cex.crypto.trading.exception.InsufficientBalanceException;
import cex.crypto.trading.mapper.UserBalanceMapper;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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

    @Value("${cache.balance.local.ttl.seconds:1}")
    private int localTtlSeconds;

    @Value("${cache.balance.redis.ttl.minutes:5}")
    private int redisTtlMinutes;

    // L1 Cache: Caffeine (local, 1 second TTL)
    private final Cache<String, UserBalance> localCache = Caffeine.newBuilder()
            .maximumSize(10_000)
            .expireAfterWrite(1, TimeUnit.SECONDS)
            .recordStats()
            .build();

    /**
     * Get balance (Read Path: L1 → L2 → L3)
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

        // L3: Load from database
        balance = balanceMapper.findByUserIdAndCurrency(userId, currency);
        if (balance == null) {
            // Create initial balance if not exists
            balance = UserBalance.builder()
                    .userId(userId)
                    .currency(currency)
                    .availableBalance(BigDecimal.ZERO)
                    .frozenBalance(BigDecimal.ZERO)
                    .build();
            balanceMapper.insert(balance);
            log.info("Created initial balance: userId={}, currency={}", userId, currency);
        }

        // Update L2 and L1 caches
        redisTemplate.opsForValue().set(cacheKey, balance, redisTtlMinutes, TimeUnit.MINUTES);
        localCache.put(cacheKey, balance);
        log.debug("L3 cache miss, loaded from DB: {}", cacheKey);

        return balance;
    }

    /**
     * Update available balance (Write-Through: L3 → L2 → L1)
     */
    @Transactional
    public UserBalance updateAvailableBalance(Long userId, String currency, BigDecimal amount) {
        String cacheKey = buildCacheKey(userId, currency);

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

        // L2: Update Redis (Write-Through)
        redisTemplate.opsForValue().set(cacheKey, updated, redisTtlMinutes, TimeUnit.MINUTES);

        // L1: Update Caffeine (Write-Through)
        localCache.put(cacheKey, updated);

        return updated;
    }

    /**
     * Update frozen balance
     */
    @Transactional
    public UserBalance updateFrozenBalance(Long userId, String currency, BigDecimal amount) {
        String cacheKey = buildCacheKey(userId, currency);

        // L3: Update database
        balanceMapper.updateFrozenBalance(userId, currency, amount);
        UserBalance updated = balanceMapper.findByUserIdAndCurrency(userId, currency);
        log.info("Updated frozen balance: userId={}, currency={}, amount={}, newBalance={}",
                 userId, currency, amount, updated.getFrozenBalance());

        // L2 & L1: Write-Through
        redisTemplate.opsForValue().set(cacheKey, updated, redisTtlMinutes, TimeUnit.MINUTES);
        localCache.put(cacheKey, updated);

        return updated;
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
}
