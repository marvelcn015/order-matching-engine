package cex.crypto.trading.service.cache;

import cex.crypto.trading.exception.LockAcquisitionException;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Distributed lock service for cache breakdown protection
 * Prevents cache stampede when multiple concurrent requests hit an expired cache entry
 */
@Service
@Slf4j
public class DistributedLockService {

    @Autowired
    private RedissonClient redissonClient;

    @Value("${cache.lock.wait.time.ms:3000}")
    private long waitTimeMs;

    @Value("${cache.lock.lease.time.ms:10000}")
    private long leaseTimeMs;

    @Value("${cache.lock.enabled:true}")
    private boolean enabled;

    /**
     * Execute action with distributed lock protection
     * Uses try-lock pattern to prevent indefinite waiting
     *
     * @param lockKey the Redis key for the lock
     * @param action the action to execute while holding the lock
     * @param <T> the return type of the action
     * @return the result of the action
     * @throws LockAcquisitionException if lock cannot be acquired within wait time
     */
    public <T> T executeWithLock(String lockKey, Supplier<T> action) {
        if (!enabled) {
            log.debug("Distributed lock disabled, executing action directly");
            return action.get();
        }

        RLock lock = redissonClient.getLock(lockKey);

        try {
            // Try to acquire lock with wait time and auto-release lease time
            boolean acquired = lock.tryLock(waitTimeMs, leaseTimeMs, TimeUnit.MILLISECONDS);

            if (!acquired) {
                log.warn("Failed to acquire lock: {} after {}ms", lockKey, waitTimeMs);
                throw new LockAcquisitionException("Cannot acquire lock: " + lockKey);
            }

            log.debug("Lock acquired: {}", lockKey);

            // Execute action while holding the lock
            return action.get();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while acquiring lock: {}", lockKey, e);
            throw new LockAcquisitionException("Interrupted while acquiring lock: " + lockKey, e);
        } finally {
            // Release lock only if held by current thread
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
                log.debug("Lock released: {}", lockKey);
            }
        }
    }

    /**
     * Build lock key for user balance cache
     * Fine-grained locking per user-currency to minimize contention
     *
     * @param userId the user ID
     * @param currency the currency code
     * @return the lock key
     */
    public String buildBalanceLockKey(Long userId, String currency) {
        return String.format("lock:cache:user:balance:%d:%s", userId, currency);
    }

    /**
     * Build lock key for user entity cache
     *
     * @param userId the user ID
     * @return the lock key
     */
    public String buildUserLockKey(Long userId) {
        return String.format("lock:cache:user:%d", userId);
    }

    /**
     * Build lock key for order entity cache
     *
     * @param orderId the order ID
     * @return the lock key
     */
    public String buildOrderLockKey(Long orderId) {
        return String.format("lock:cache:order:%d", orderId);
    }

    /**
     * Build lock key for user creation to prevent duplicate username
     *
     * @param username the username
     * @return the lock key
     */
    public String buildUserCreationLockKey(String username) {
        return String.format("lock:user:create:%s", username);
    }

    /**
     * Get lock configuration information
     *
     * @return configuration info string
     */
    public String getLockConfig() {
        return String.format("DistributedLock[enabled=%s, waitTime=%dms, leaseTime=%dms]",
                enabled, waitTimeMs, leaseTimeMs);
    }
}
