package cex.crypto.trading.config;

import cex.crypto.trading.event.OrderCreatedEvent;
import cex.crypto.trading.event.OrderStatusEvent;
import cex.crypto.trading.event.TradeExecutedEvent;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer Configuration
 * Configures producers for publishing events to Kafka topics
 */
@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Base producer configuration
     * Performance tuned as per requirements:
     * - batch.size=16384 (16KB batches)
     * - linger.ms=10 (10ms batching window)
     * - acks=1 (leader acknowledgment only, performance priority)
     */
    @Bean
    public Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();

        // Connection
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Serialization
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Performance tuning (as per requirements)
        props.put(ProducerConfig.BATCH_SIZE_CONFIG, 16384); // 16KB batch size
        props.put(ProducerConfig.LINGER_MS_CONFIG, 10); // 10ms batching window
        props.put(ProducerConfig.ACKS_CONFIG, "1"); // Leader ack only (performance priority)
        props.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "snappy"); // Fast compression
        props.put(ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432); // 32MB buffer

        // Reliability
        props.put(ProducerConfig.RETRIES_CONFIG, 3); // Retry 3 times on transient failures
        props.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, false);

        // Timeouts
        props.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30000); // 30 seconds
        props.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120000); // 2 minutes

        return props;
    }

    /**
     * Producer factory for OrderCreatedEvent
     */
    @Bean
    public ProducerFactory<String, OrderCreatedEvent> orderEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    /**
     * KafkaTemplate for publishing OrderCreatedEvent to order-input topic
     */
    @Bean
    public KafkaTemplate<String, OrderCreatedEvent> orderKafkaTemplate() {
        return new KafkaTemplate<>(orderEventProducerFactory());
    }

    /**
     * Producer factory for TradeExecutedEvent
     */
    @Bean
    public ProducerFactory<String, TradeExecutedEvent> tradeEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    /**
     * KafkaTemplate for publishing TradeExecutedEvent to trade-output topic
     */
    @Bean
    public KafkaTemplate<String, TradeExecutedEvent> tradeKafkaTemplate() {
        return new KafkaTemplate<>(tradeEventProducerFactory());
    }

    /**
     * Producer factory for OrderStatusEvent
     */
    @Bean
    public ProducerFactory<String, OrderStatusEvent> statusEventProducerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    /**
     * KafkaTemplate for publishing OrderStatusEvent to order-status-update topic
     */
    @Bean
    public KafkaTemplate<String, OrderStatusEvent> statusKafkaTemplate() {
        return new KafkaTemplate<>(statusEventProducerFactory());
    }
}
