package cex.crypto.trading.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.scheduling.annotation.EnableScheduling;
import redis.embedded.RedisServer;

import java.io.IOException;

/**
 * Test configuration for embedded Redis server.
 *
 * Starts an embedded Redis instance on port 6370 for integration tests.
 * Automatically starts when test context loads and stops on context close.
 */
@Slf4j
@TestConfiguration
@EnableScheduling
public class RedisTestConfig {

    private RedisServer redisServer;

    /**
     * Start embedded Redis server on port 6370.
     * Different port from production (6379) to avoid conflicts.
     */
    @PostConstruct
    public void startRedis() {
        try {
            redisServer = RedisServer.builder()
                    .port(6370)
                    .setting("maxmemory 128M")
                    .build();
            redisServer.start();
            log.info("Embedded Redis started on port 6370");
        } catch (Exception e) {
            log.error("Failed to start embedded Redis", e);
            throw new RuntimeException("Failed to start embedded Redis", e);
        }
    }

    /**
     * Stop embedded Redis server on test context close.
     */
    @PreDestroy
    public void stopRedis() {
        if (redisServer != null) {
            redisServer.stop();
            log.info("Embedded Redis stopped");
        }
    }
}
