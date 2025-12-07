package cex.crypto.trading.interceptor;

import cex.crypto.trading.exception.RateLimitExceededException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RRateLimiter;
import org.redisson.api.RateIntervalUnit;
import org.redisson.api.RateType;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.TimeUnit;

/**
 * Rate limiter interceptor using Redisson RRateLimiter
 * Token Bucket algorithm: 10 requests per second per user
 */
@Component
@Slf4j
public class RateLimiterInterceptor implements HandlerInterceptor {

    @Autowired
    private RedissonClient redissonClient;

    @Value("${rate.limiter.requests.per.second:10}")
    private long requestsPerSecond;

    @Value("${rate.limiter.permit.timeout.ms:100}")
    private long permitTimeoutMs;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // Extract user ID from request header
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader == null || userIdHeader.isEmpty()) {
            log.warn("Missing X-User-Id header, rate limiting skipped for: {}", request.getRequestURI());
            return true; // Skip rate limiting if no user ID
        }

        Long userId;
        try {
            userId = Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            log.warn("Invalid X-User-Id header: {}", userIdHeader);
            return true; // Skip rate limiting for invalid user ID
        }

        // Get or create rate limiter for this user
        String rateLimiterKey = "rate_limit:user:" + userId;
        RRateLimiter rateLimiter = redissonClient.getRateLimiter(rateLimiterKey);

        // Initialize rate limiter if not set (idempotent)
        if (!rateLimiter.isExists()) {
            rateLimiter.trySetRate(RateType.OVERALL, requestsPerSecond, 1, RateIntervalUnit.SECONDS);
            rateLimiter.expire(60, TimeUnit.SECONDS); // Expire after 60s of inactivity
            log.debug("Initialized rate limiter for userId={}: {} req/s", userId, requestsPerSecond);
        }

        // Try to acquire permit
        boolean permitted = rateLimiter.tryAcquire(1, permitTimeoutMs, TimeUnit.MILLISECONDS);

        if (!permitted) {
            // Get rate limiter config for response headers
            long availablePermits = rateLimiter.availablePermits();
            long resetTimeSeconds = System.currentTimeMillis() / 1000 + 1; // Next second

            // Set rate limit headers
            response.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerSecond));
            response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, availablePermits)));
            response.setHeader("X-RateLimit-Reset", String.valueOf(resetTimeSeconds));

            log.warn("Rate limit exceeded for userId={}, URI={}", userId, request.getRequestURI());
            throw new RateLimitExceededException("Rate limit exceeded: " + requestsPerSecond + " requests per second");
        }

        // Add rate limit info to response headers (success case)
        long availablePermits = rateLimiter.availablePermits();
        long resetTimeSeconds = System.currentTimeMillis() / 1000 + 1;
        response.setHeader("X-RateLimit-Limit", String.valueOf(requestsPerSecond));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(Math.max(0, availablePermits)));
        response.setHeader("X-RateLimit-Reset", String.valueOf(resetTimeSeconds));

        log.debug("Rate limit check passed for userId={}, remaining={}", userId, availablePermits);
        return true;
    }
}
