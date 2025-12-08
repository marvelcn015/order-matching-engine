package cex.crypto.trading.service.sse;

import cex.crypto.trading.event.OrderStatusEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry for managing SSE connections
 * Manages user connections and broadcasts order status updates
 */
@Slf4j
@Service
public class SseEmitterRegistry {

    /**
     * Map of userId to Set of SSE emitters
     * One user can have multiple connections (e.g., multiple browser tabs)
     */
    private final Map<Long, Set<SseEmitter>> emittersByUser = new ConcurrentHashMap<>();

    /**
     * Add SSE emitter for a user
     *
     * @param userId the user ID
     * @param emitter the SSE emitter
     */
    public void addEmitter(Long userId, SseEmitter emitter) {
        emittersByUser.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet())
                .add(emitter);

        log.info("Added SSE emitter: userId={}, totalEmitters={}, totalUsers={}",
                userId, emittersByUser.get(userId).size(), emittersByUser.size());
    }

    /**
     * Remove SSE emitter for a user
     *
     * @param userId the user ID
     * @param emitter the SSE emitter to remove
     */
    public void removeEmitter(Long userId, SseEmitter emitter) {
        Set<SseEmitter> emitters = emittersByUser.get(userId);
        if (emitters != null) {
            emitters.remove(emitter);

            // Remove user entry if no more emitters
            if (emitters.isEmpty()) {
                emittersByUser.remove(userId);
                log.info("Removed last SSE emitter for user: userId={}, totalUsers={}",
                        userId, emittersByUser.size());
            } else {
                log.info("Removed SSE emitter: userId={}, remainingEmitters={}",
                        userId, emitters.size());
            }
        }
    }

    /**
     * Send order status event to all emitters for a user
     *
     * @param userId the user ID
     * @param event the order status event to send
     */
    public void sendToUser(Long userId, OrderStatusEvent event) {
        Set<SseEmitter> emitters = emittersByUser.get(userId);

        if (emitters == null || emitters.isEmpty()) {
            log.debug("No SSE emitters for user: userId={}", userId);
            return;
        }

        List<SseEmitter> deadEmitters = new ArrayList<>();

        // Send event to all user's emitters
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event()
                    .name("order-status-update")
                    .data(event)
                    .id(event.getOrderId() + "-" + event.getTimestamp())
                );

                log.debug("Sent SSE event to user: userId={}, orderId={}, status={}",
                        userId, event.getOrderId(), event.getStatus());

            } catch (IOException e) {
                log.warn("Failed to send SSE event: userId={}, orderId={}, error={}",
                        userId, event.getOrderId(), e.getMessage());

                // Mark emitter as dead (will be removed after loop)
                deadEmitters.add(emitter);
            }
        }

        // Remove dead emitters
        deadEmitters.forEach(emitter -> removeEmitter(userId, emitter));

        if (!deadEmitters.isEmpty()) {
            log.info("Removed {} dead SSE emitter(s) for userId={}", deadEmitters.size(), userId);
        }
    }

    /**
     * Get the number of active connections for a user
     *
     * @param userId the user ID
     * @return number of active SSE connections
     */
    public int getConnectionCount(Long userId) {
        Set<SseEmitter> emitters = emittersByUser.get(userId);
        return emitters == null ? 0 : emitters.size();
    }

    /**
     * Get total number of active users with SSE connections
     *
     * @return number of users
     */
    public int getTotalUserCount() {
        return emittersByUser.size();
    }

    /**
     * Get total number of active SSE connections across all users
     *
     * @return total connection count
     */
    public int getTotalConnectionCount() {
        return emittersByUser.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    /**
     * Remove all emitters for a user (for admin/cleanup purposes)
     *
     * @param userId the user ID
     */
    public void removeAllEmittersForUser(Long userId) {
        Set<SseEmitter> emitters = emittersByUser.remove(userId);

        if (emitters != null) {
            // Complete all emitters
            emitters.forEach(emitter -> {
                try {
                    emitter.complete();
                } catch (Exception e) {
                    log.warn("Error completing emitter: userId={}, error={}", userId, e.getMessage());
                }
            });

            log.info("Removed all SSE emitters for user: userId={}, count={}", userId, emitters.size());
        }
    }
}
