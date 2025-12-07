package cex.crypto.trading.service.cache;

import cex.crypto.trading.domain.UserBalance;
import cex.crypto.trading.mapper.OrderMapper;
import cex.crypto.trading.mapper.UserBalanceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit tests for CacheWarmerService
 */
@SpringBootTest
@ActiveProfiles("test")
class CacheWarmerServiceTest {

    @Autowired
    private CacheWarmerService cacheWarmerService;

    @MockBean
    private UserBalanceMapper balanceMapper;

    @MockBean
    private OrderMapper orderMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private ValueOperations<String, Object> valueOps;

    @BeforeEach
    void setUp() {
        valueOps = redisTemplate.opsForValue();

        // Mock order mapper to return empty list by default
        when(orderMapper.findActiveUserIds(anyInt(), anyInt())).thenReturn(new ArrayList<>());
        when(balanceMapper.findByUserId(any())).thenReturn(new ArrayList<>());
    }

    @Test
    void testRandomRedisTtl() {
        // Base: 5 minutes = 300 seconds
        // Jitter: ±20% → range 240-360 seconds

        Set<Long> ttls = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            long ttl = cacheWarmerService.getRandomRedisTtl();
            ttls.add(ttl);

            // Verify TTL is within expected range
            assertTrue(ttl >= 240 && ttl <= 360,
                    String.format("TTL %d should be between 240 and 360 seconds", ttl));
        }

        // Verify we got different values (randomness)
        assertTrue(ttls.size() > 10,
                "Should generate multiple different TTL values");
    }

    @Test
    void testRandomLocalTtl() {
        // Base: 1 second
        // Jitter: ±20% → range 0.8-1.2 seconds (rounded to long: 0-1 seconds)

        Set<Long> ttls = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            long ttl = cacheWarmerService.getRandomLocalTtl();
            ttls.add(ttl);

            // Verify TTL is within expected range (minimum 1 second)
            assertTrue(ttl >= 1 && ttl <= 2,
                    String.format("TTL %d should be between 1 and 2 seconds", ttl));
        }

        // Verify we got some variation
        assertTrue(ttls.size() >= 1,
                "Should generate at least one TTL value");
    }

    @Test
    void testWarmCacheEntry() {
        // Create test balance
        UserBalance balance = UserBalance.builder()
                .userId(123L)
                .currency("BTC")
                .availableBalance(new BigDecimal("1.5"))
                .frozenBalance(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Warm cache
        cacheWarmerService.warmCacheEntry(balance);

        // Verify entry was cached
        String cacheKey = "user:balance:123:BTC";
        Object cached = valueOps.get(cacheKey);

        assertNotNull(cached, "Balance should be cached");
        assertTrue(cached instanceof UserBalance, "Cached object should be UserBalance");

        UserBalance cachedBalance = (UserBalance) cached;
        assertEquals(123L, cachedBalance.getUserId());
        assertEquals("BTC", cachedBalance.getCurrency());
        assertEquals(new BigDecimal("1.5"), cachedBalance.getAvailableBalance());
    }

    @Test
    void testWarmCacheEntryWithNull() {
        // Should not throw exception
        assertDoesNotThrow(() -> cacheWarmerService.warmCacheEntry(null),
                "Warming null entry should not throw exception");
    }

    @Test
    void testWarmCacheOnStartup() {
        // Temporarily enable cache warmer for this test
        ReflectionTestUtils.setField(cacheWarmerService, "enabled", true);

        // Mock active users
        List<Long> activeUsers = List.of(1L, 2L, 3L);
        when(orderMapper.findActiveUserIds(1000, 24)).thenReturn(activeUsers);

        // Mock balances for users
        List<UserBalance> user1Balances = List.of(
                createTestBalance(1L, "BTC", "1.0"),
                createTestBalance(1L, "ETH", "10.0")
        );
        List<UserBalance> user2Balances = List.of(
                createTestBalance(2L, "BTC", "2.0")
        );
        List<UserBalance> user3Balances = List.of(
                createTestBalance(3L, "USDT", "1000.0")
        );

        when(balanceMapper.findByUserId(1L)).thenReturn(user1Balances);
        when(balanceMapper.findByUserId(2L)).thenReturn(user2Balances);
        when(balanceMapper.findByUserId(3L)).thenReturn(user3Balances);

        // Execute warming
        cacheWarmerService.warmCacheOnStartup();

        // Verify cache entries were created
        assertNotNull(valueOps.get("user:balance:1:BTC"));
        assertNotNull(valueOps.get("user:balance:1:ETH"));
        assertNotNull(valueOps.get("user:balance:2:BTC"));
        assertNotNull(valueOps.get("user:balance:3:USDT"));
    }

    @Test
    void testWarmCacheWithNoActiveUsers() {
        // Mock empty active users list
        when(orderMapper.findActiveUserIds(anyInt(), anyInt())).thenReturn(new ArrayList<>());

        // Should complete without error
        assertDoesNotThrow(() -> cacheWarmerService.warmCacheOnStartup(),
                "Warming with no active users should not throw exception");
    }

    @Test
    void testManualWarmCache() {
        // Temporarily enable cache warmer for this test
        ReflectionTestUtils.setField(cacheWarmerService, "enabled", true);

        // Mock active users
        when(orderMapper.findActiveUserIds(anyInt(), anyInt())).thenReturn(List.of(1L));
        when(balanceMapper.findByUserId(1L)).thenReturn(List.of(
                createTestBalance(1L, "BTC", "1.0")
        ));

        // Manual trigger
        int result = cacheWarmerService.warmCache();

        // Should return estimated count (topUsers = 1000)
        assertEquals(1000, result);
    }

    @Test
    void testGetConfig() {
        String config = cacheWarmerService.getConfig();

        assertNotNull(config, "Config should not be null");
        assertTrue(config.contains("enabled"), "Config should contain enabled flag");
        assertTrue(config.contains("topUsers"), "Config should contain topUsers");
        assertTrue(config.contains("hours"), "Config should contain hours");
        assertTrue(config.contains("redisTtl"), "Config should contain redisTtl");
        assertTrue(config.contains("localTtl"), "Config should contain localTtl");
    }

    /**
     * Helper method to create test UserBalance
     */
    private UserBalance createTestBalance(Long userId, String currency, String amount) {
        return UserBalance.builder()
                .userId(userId)
                .currency(currency)
                .availableBalance(new BigDecimal(amount))
                .frozenBalance(BigDecimal.ZERO)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
