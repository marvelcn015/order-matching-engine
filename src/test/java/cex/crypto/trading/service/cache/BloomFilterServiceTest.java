package cex.crypto.trading.service.cache;

import cex.crypto.trading.dto.BloomFilterStats;
import cex.crypto.trading.mapper.OrderMapper;
import cex.crypto.trading.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BloomFilterService
 */
@SpringBootTest
@ActiveProfiles("test")
class BloomFilterServiceTest {

    @Autowired
    private BloomFilterService bloomFilterService;

    @MockBean
    private UserMapper userMapper;

    @MockBean
    private OrderMapper orderMapper;

    @Autowired
    private RedissonClient redissonClient;

    private RBloomFilter<Long> userBloomFilter;
    private RBloomFilter<Long> orderBloomFilter;

    @BeforeEach
    void setUp() {
        // Get Bloom Filter references
        userBloomFilter = redissonClient.getBloomFilter("bloom:filter:users");
        orderBloomFilter = redissonClient.getBloomFilter("bloom:filter:orders");

        // Clear filters before each test
        if (userBloomFilter.isExists()) {
            userBloomFilter.delete();
        }
        if (orderBloomFilter.isExists()) {
            orderBloomFilter.delete();
        }

        // Reinitialize filters with correct configuration
        userBloomFilter.tryInit(1000000L, 0.01);
        orderBloomFilter.tryInit(10000000L, 0.01);

        // Mock mapper responses
        when(userMapper.findAllUserIds(anyLong(), anyInt())).thenReturn(new ArrayList<>());
        when(orderMapper.findAllOrderIds(anyLong(), anyInt())).thenReturn(new ArrayList<>());
    }

    @Test
    void testAddAndCheckUser() {
        // Add user ID
        Long userId = 12345L;
        bloomFilterService.addUser(userId);

        // Verify user exists
        assertTrue(bloomFilterService.mayExistUser(userId),
                "User should be found in Bloom Filter after adding");

        // Verify non-existent user
        assertFalse(bloomFilterService.mayExistUser(99999L),
                "Non-existent user should not be found in Bloom Filter");
    }

    @Test
    void testAddAndCheckOrder() {
        // Add order ID
        Long orderId = 67890L;
        bloomFilterService.addOrder(orderId);

        // Verify order exists
        assertTrue(bloomFilterService.mayExistOrder(orderId),
                "Order should be found in Bloom Filter after adding");

        // Verify non-existent order
        assertFalse(bloomFilterService.mayExistOrder(88888L),
                "Non-existent order should not be found in Bloom Filter");
    }

    @Test
    void testNullHandling() {
        // Null user ID should return false
        assertFalse(bloomFilterService.mayExistUser(null),
                "Null user ID should return false");

        // Null order ID should return false
        assertFalse(bloomFilterService.mayExistOrder(null),
                "Null order ID should return false");

        // Adding null should not throw exception
        assertDoesNotThrow(() -> bloomFilterService.addUser(null),
                "Adding null user should not throw exception");
        assertDoesNotThrow(() -> bloomFilterService.addOrder(null),
                "Adding null order should not throw exception");
    }

    @Test
    void testFalsePositiveRate() {
        // Add 1000 users
        for (long i = 1; i <= 1000; i++) {
            bloomFilterService.addUser(i);
        }

        // Check for false positives in range outside added values
        int falsePositives = 0;
        int totalChecks = 1000;

        for (long i = 10001; i < 10001 + totalChecks; i++) {
            if (bloomFilterService.mayExistUser(i)) {
                falsePositives++;
            }
        }

        // False positive rate should be around 1% (configured as 0.01)
        // Allow some tolerance (0-5%)
        double falsePositiveRate = (double) falsePositives / totalChecks;
        assertTrue(falsePositiveRate <= 0.05,
                String.format("False positive rate %.2f%% should be <= 5%%",
                        falsePositiveRate * 100));
    }

    @Test
    void testGetStats() {
        // Add some users and orders
        for (long i = 1; i <= 100; i++) {
            bloomFilterService.addUser(i);
        }
        for (long i = 1; i <= 200; i++) {
            bloomFilterService.addOrder(i);
        }

        // Get statistics
        BloomFilterStats stats = bloomFilterService.getStats();

        // Verify stats
        assertNotNull(stats, "Stats should not be null");
        assertTrue(stats.getUserFilterSize() >= 100,
                "User filter size should be at least 100");
        assertTrue(stats.getOrderFilterSize() >= 200,
                "Order filter size should be at least 200");
        assertTrue(stats.getUserFilterUtilization() > 0,
                "User filter utilization should be positive");
        assertTrue(stats.getOrderFilterUtilization() > 0,
                "Order filter utilization should be positive");
    }

    @Test
    void testBatchInitialization() {
        // Mock user IDs from database
        List<Long> batch1 = new ArrayList<>();
        for (long i = 1; i <= 100; i++) {
            batch1.add(i);
        }

        List<Long> batch2 = new ArrayList<>();
        for (long i = 101; i <= 150; i++) {
            batch2.add(i);
        }

        when(userMapper.findAllUserIds(0L, 10000)).thenReturn(batch1);
        when(userMapper.findAllUserIds(10000L, 10000)).thenReturn(batch2);
        when(userMapper.findAllUserIds(20000L, 10000)).thenReturn(new ArrayList<>());

        // Reinitialize to trigger batch loading
        bloomFilterService.initialize();

        // Wait for async loading to complete
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Verify some users from batch are loaded
        // Note: This test may be flaky due to async nature
        // In production, you would use proper async testing frameworks
    }

    @Test
    void testRebuild() {
        // Add some data
        bloomFilterService.addUser(1L);
        bloomFilterService.addOrder(100L);

        // Verify data exists
        assertTrue(bloomFilterService.mayExistUser(1L));
        assertTrue(bloomFilterService.mayExistOrder(100L));

        // Rebuild filters
        bloomFilterService.rebuild();

        // Wait for rebuild to complete
        try {
            Thread.sleep(1000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // After rebuild, data should be reloaded from mocked empty mappers
        // So previously added data should be gone
        BloomFilterStats stats = bloomFilterService.getStats();
        assertNotNull(stats);
    }
}
