package cex.crypto.trading.service.redis;

import cex.crypto.trading.domain.Order;
import cex.crypto.trading.domain.OrderBook;
import cex.crypto.trading.enums.OrderSide;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ConcurrentSkipListMap;

/**
 * Service for Redis-based order book persistence.
 *
 * Data Model:
 * - orderbook:{symbol}:buy:prices -> ZSet (score=-price, member=price_string)
 * - orderbook:{symbol}:sell:prices -> ZSet (score=price, member=price_string)
 * - orderbook:{symbol}:buy:price:{price} -> List (orderId1, orderId2, ...)
 * - orderbook:{symbol}:sell:price:{price} -> List (orderId1, orderId2, ...)
 * - order:{orderId} -> Hash (Order fields)
 * - orderbook:{symbol}:metadata -> Hash (version, updatedAt)
 */
@Slf4j
@Service
public class RedisOrderBookService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    /**
     * Save entire order book to Redis using pipelined operations.
     * Uses MULTI/EXEC for atomicity.
     *
     * @param orderBook The order book to save
     */
    public void saveToRedis(OrderBook orderBook) {
        String symbol = orderBook.getSymbol();

        try {
            // Use pipeline for atomic multi-key operations
            redisTemplate.execute(new SessionCallback<Object>() {
                @Override
                public Object execute(RedisOperations operations) throws DataAccessException {
                    operations.multi();

                    // 1. Delete old data
                    deleteOrderBookKeys(symbol, operations);

                    // 2. Save buy orders
                    saveSideOrders(symbol, OrderSide.BUY, orderBook.getBuyOrders(), operations);

                    // 3. Save sell orders
                    saveSideOrders(symbol, OrderSide.SELL, orderBook.getSellOrders(), operations);

                    // 4. Save metadata
                    saveMetadata(symbol, orderBook.getVersion(), orderBook.getUpdatedAt(), operations);

                    return operations.exec();
                }
            });

            log.debug("Saved order book to Redis: symbol={}, version={}, buyLevels={}, sellLevels={}",
                    symbol, orderBook.getVersion(),
                    orderBook.getBuyOrders().size(), orderBook.getSellOrders().size());

        } catch (Exception e) {
            log.error("Error saving order book to Redis: symbol={}, error={}", symbol, e.getMessage(), e);
            throw new RuntimeException("Failed to save order book to Redis", e);
        }
    }

    /**
     * Load order book from Redis.
     *
     * @param symbol Trading symbol
     * @return OrderBook instance or null if not found
     */
    public OrderBook loadFromRedis(String symbol) {
        try {
            // 1. Check if metadata exists
            String metadataKey = buildMetadataKey(symbol);
            Boolean hasMetadata = redisTemplate.hasKey(metadataKey);

            if (hasMetadata == null || !hasMetadata) {
                log.debug("Order book not found in Redis: {}", symbol);
                return null;
            }

            // 2. Load metadata
            Map<Object, Object> metadata = redisTemplate.opsForHash().entries(metadataKey);
            if (metadata.isEmpty()) {
                log.warn("Metadata key exists but is empty: {}", symbol);
                return null;
            }

            Integer version = Integer.valueOf((String) metadata.get("version"));
            LocalDateTime updatedAt = LocalDateTime.parse((String) metadata.get("updatedAt"), DATE_TIME_FORMATTER);

            // 3. Build OrderBook with proper comparators
            OrderBook orderBook = OrderBook.builder()
                    .symbol(symbol)
                    .version(version)
                    .updatedAt(updatedAt)
                    .build();

            // 4. Load buy orders (descending)
            ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> buyOrders =
                    loadSideOrders(symbol, OrderSide.BUY, Comparator.reverseOrder());
            orderBook.setBuyOrders(buyOrders);

            // 5. Load sell orders (ascending)
            ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> sellOrders =
                    loadSideOrders(symbol, OrderSide.SELL, Comparator.naturalOrder());
            orderBook.setSellOrders(sellOrders);

            log.debug("Loaded order book from Redis: symbol={}, version={}, buyLevels={}, sellLevels={}",
                    symbol, version, buyOrders.size(), sellOrders.size());

            return orderBook;

        } catch (Exception e) {
            log.error("Error loading order book from Redis: symbol={}, error={}", symbol, e.getMessage(), e);
            return null; // Graceful degradation - fallback to MySQL
        }
    }

    /**
     * Delete all Redis keys for a symbol.
     *
     * @param symbol Trading symbol
     */
    public void deleteFromRedis(String symbol) {
        try {
            Set<String> keys = redisTemplate.keys(String.format("orderbook:%s:*", symbol));
            if (keys != null && !keys.isEmpty()) {
                redisTemplate.delete(keys);
                log.info("Deleted order book from Redis: symbol={}, keys={}", symbol, keys.size());
            }
        } catch (Exception e) {
            log.error("Error deleting order book from Redis: symbol={}, error={}", symbol, e.getMessage(), e);
        }
    }

    /**
     * Delete order book keys within a transaction.
     */
    private void deleteOrderBookKeys(String symbol, RedisOperations operations) {
        // Delete price ZSets
        operations.delete(buildPricesKey(symbol, OrderSide.BUY));
        operations.delete(buildPricesKey(symbol, OrderSide.SELL));

        // Delete metadata
        operations.delete(buildMetadataKey(symbol));

        // Note: Price list keys and order hashes will be overwritten
        // We could track them explicitly, but for simplicity, we rely on overwrites
    }

    /**
     * Save orders for one side (BUY or SELL).
     */
    private void saveSideOrders(
            String symbol,
            OrderSide side,
            ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> orders,
            RedisOperations operations) {

        String pricesKey = buildPricesKey(symbol, side);

        for (Map.Entry<BigDecimal, ConcurrentLinkedQueue<Order>> entry : orders.entrySet()) {
            BigDecimal price = entry.getKey();
            ConcurrentLinkedQueue<Order> ordersAtPrice = entry.getValue();

            // 1. Add price to ZSet
            double score = calculateScore(price, side);
            operations.opsForZSet().add(pricesKey, price.toPlainString(), score);

            // 2. Delete old list and create new one
            String priceListKey = buildPriceListKey(symbol, side, price);
            operations.delete(priceListKey);

            // 3. Add order IDs to List (maintains FIFO)
            for (Order order : ordersAtPrice) {
                operations.opsForList().rightPush(priceListKey, order.getOrderId().toString());

                // 4. Save order details to Hash
                String orderKey = buildOrderKey(order.getOrderId());
                Map<String, Object> orderData = serializeOrder(order);
                operations.opsForHash().putAll(orderKey, orderData);
            }
        }
    }

    /**
     * Load orders for one side.
     */
    private ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> loadSideOrders(
            String symbol,
            OrderSide side,
            Comparator<BigDecimal> comparator) {

        ConcurrentSkipListMap<BigDecimal, ConcurrentLinkedQueue<Order>> result =
                new ConcurrentSkipListMap<>(comparator);

        String pricesKey = buildPricesKey(symbol, side);

        // Get all prices in order (ZSet maintains sort)
        Set<String> prices = stringRedisTemplate.opsForZSet().range(pricesKey, 0, -1);

        if (prices == null || prices.isEmpty()) {
            return result;
        }

        for (String priceStr : prices) {
            BigDecimal price = new BigDecimal(priceStr);

            // Get order IDs at this price
            String priceListKey = buildPriceListKey(symbol, side, price);
            List<String> orderIds = stringRedisTemplate.opsForList().range(priceListKey, 0, -1);

            if (orderIds == null || orderIds.isEmpty()) {
                log.warn("Price level exists but no orders: symbol={}, side={}, price={}",
                        symbol, side, price);
                continue;
            }

            // Load each order
            ConcurrentLinkedQueue<Order> ordersAtPrice = new ConcurrentLinkedQueue<>();
            for (String orderIdStr : orderIds) {
                Order order = loadOrder(Long.valueOf(orderIdStr));
                if (order != null) {
                    ordersAtPrice.offer(order);
                }
            }

            if (!ordersAtPrice.isEmpty()) {
                result.put(price, ordersAtPrice);
            }
        }

        return result;
    }

    /**
     * Load single order from Redis Hash.
     */
    private Order loadOrder(Long orderId) {
        try {
            String orderKey = buildOrderKey(orderId);
            Map<Object, Object> orderData = redisTemplate.opsForHash().entries(orderKey);

            if (orderData.isEmpty()) {
                log.warn("Order not found in Redis: orderId={}", orderId);
                return null;
            }

            return deserializeOrder(orderData);

        } catch (Exception e) {
            log.error("Error loading order from Redis: orderId={}, error={}", orderId, e.getMessage());
            return null;
        }
    }

    /**
     * Save metadata (version, updatedAt).
     */
    private void saveMetadata(String symbol, Integer version, LocalDateTime updatedAt, RedisOperations operations) {
        String metadataKey = buildMetadataKey(symbol);
        Map<String, String> metadata = new HashMap<>();
        metadata.put("version", version.toString());
        metadata.put("updatedAt", updatedAt.format(DATE_TIME_FORMATTER));
        operations.opsForHash().putAll(metadataKey, metadata);
    }

    /**
     * Calculate ZSet score based on side.
     * BUY: negative (descending), SELL: positive (ascending)
     */
    private double calculateScore(BigDecimal price, OrderSide side) {
        double priceDouble = price.doubleValue();
        return side == OrderSide.BUY ? -priceDouble : priceDouble;
    }

    /**
     * Serialize Order to Map for Redis Hash.
     */
    private Map<String, Object> serializeOrder(Order order) {
        try {
            return objectMapper.convertValue(order, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Error serializing order: orderId={}, error={}", order.getOrderId(), e.getMessage());
            throw new RuntimeException("Failed to serialize order", e);
        }
    }

    /**
     * Deserialize Order from Map.
     */
    private Order deserializeOrder(Map<Object, Object> data) {
        try {
            // Convert to proper type map
            Map<String, Object> convertedData = new HashMap<>();
            for (Map.Entry<Object, Object> entry : data.entrySet()) {
                convertedData.put(entry.getKey().toString(), entry.getValue());
            }

            return objectMapper.convertValue(convertedData, Order.class);
        } catch (Exception e) {
            log.error("Error deserializing order from Redis: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Build key for price ZSet.
     */
    private String buildPricesKey(String symbol, OrderSide side) {
        return String.format("orderbook:%s:%s:prices", symbol, side.name().toLowerCase());
    }

    /**
     * Build key for price level List.
     */
    private String buildPriceListKey(String symbol, OrderSide side, BigDecimal price) {
        return String.format("orderbook:%s:%s:price:%s", symbol, side.name().toLowerCase(), price.toPlainString());
    }

    /**
     * Build key for Order Hash.
     */
    private String buildOrderKey(Long orderId) {
        return String.format("order:%d", orderId);
    }

    /**
     * Build key for metadata Hash.
     */
    private String buildMetadataKey(String symbol) {
        return String.format("orderbook:%s:metadata", symbol);
    }
}
