package cex.crypto.trading.service.cache;

import cex.crypto.trading.exception.LockAcquisitionException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for DistributedLockService
 */
@SpringBootTest
@ActiveProfiles("test")
class DistributedLockServiceTest {

    @Autowired
    private DistributedLockService lockService;

    @Test
    void testExecuteWithLock() {
        String lockKey = "test:lock:1";
        AtomicInteger counter = new AtomicInteger(0);

        // Execute action with lock
        Integer result = lockService.executeWithLock(lockKey, () -> {
            counter.incrementAndGet();
            return counter.get();
        });

        assertEquals(1, result, "Action should be executed once");
        assertEquals(1, counter.get(), "Counter should be 1");
    }

    @Test
    void testConcurrentLockAcquisition() throws InterruptedException {
        String lockKey = "test:lock:concurrent";
        AtomicInteger counter = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);
        int threadCount = 10;

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Launch multiple threads trying to acquire the same lock
        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // All threads start at the same time

                    lockService.executeWithLock(lockKey, () -> {
                        counter.incrementAndGet();
                        // Simulate some work
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return counter.get();
                    });

                    successCount.incrementAndGet();
                } catch (LockAcquisitionException e) {
                    // Expected for some threads due to wait timeout
                    failureCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endLatch.countDown();
                }
            });
        }

        // Start all threads
        startLatch.countDown();

        // Wait for all threads to complete
        endLatch.await();
        executor.shutdown();

        // Verify that all increments were atomic (no race condition)
        assertEquals(successCount.get(), counter.get(),
                "Counter should match number of successful lock acquisitions");

        // At least one thread should succeed
        assertTrue(successCount.get() >= 1,
                "At least one thread should successfully acquire the lock");

        System.out.println("Success: " + successCount.get() + ", Failures: " + failureCount.get());
    }

    @Test
    void testLockKeyBuilders() {
        // Test balance lock key
        String balanceKey = lockService.buildBalanceLockKey(123L, "BTC");
        assertEquals("lock:cache:user:balance:123:BTC", balanceKey);

        // Test user lock key
        String userKey = lockService.buildUserLockKey(456L);
        assertEquals("lock:cache:user:456", userKey);

        // Test order lock key
        String orderKey = lockService.buildOrderLockKey(789L);
        assertEquals("lock:cache:order:789", orderKey);

        // Test user creation lock key
        String creationKey = lockService.buildUserCreationLockKey("testuser");
        assertEquals("lock:user:create:testuser", creationKey);
    }

    @Test
    void testExceptionHandling() {
        String lockKey = "test:lock:exception";

        // Test that exceptions in action are propagated
        assertThrows(RuntimeException.class, () -> {
            lockService.executeWithLock(lockKey, () -> {
                throw new RuntimeException("Test exception");
            });
        });
    }

    @Test
    void testReentrantLock() throws InterruptedException {
        String lockKey = "test:lock:reentrant";
        AtomicInteger counter = new AtomicInteger(0);

        // Nested lock acquisition (reentrant)
        Integer result = lockService.executeWithLock(lockKey, () -> {
            counter.incrementAndGet();

            // Try to acquire the same lock again (should succeed if reentrant)
            return lockService.executeWithLock(lockKey, () -> {
                counter.incrementAndGet();
                return counter.get();
            });
        });

        assertEquals(2, result, "Nested lock should succeed");
        assertEquals(2, counter.get(), "Counter should be 2");
    }

    @Test
    void testLockConfig() {
        String config = lockService.getLockConfig();
        assertNotNull(config, "Config should not be null");
        assertTrue(config.contains("enabled"), "Config should contain enabled flag");
        assertTrue(config.contains("waitTime"), "Config should contain wait time");
        assertTrue(config.contains("leaseTime"), "Config should contain lease time");
    }

    @Test
    void testMultipleDifferentLocks() throws InterruptedException {
        // Test that different lock keys don't interfere with each other
        List<String> lockKeys = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            lockKeys.add("test:lock:different:" + i);
        }

        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(lockKeys.size());
        ExecutorService executor = Executors.newFixedThreadPool(lockKeys.size());
        AtomicInteger successCount = new AtomicInteger(0);

        // Each thread acquires a different lock
        for (String lockKey : lockKeys) {
            executor.submit(() -> {
                try {
                    startLatch.await();

                    lockService.executeWithLock(lockKey, () -> {
                        // Simulate work
                        try {
                            Thread.sleep(50);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return null;
                    });

                    successCount.incrementAndGet();
                } catch (Exception e) {
                    // Should not fail
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        endLatch.await();
        executor.shutdown();

        // All threads should succeed since they use different locks
        assertEquals(lockKeys.size(), successCount.get(),
                "All threads should succeed with different lock keys");
    }
}
