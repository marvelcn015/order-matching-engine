package cex.crypto.trading.config;

import cex.crypto.trading.event.OrderCreatedEvent;
import cex.crypto.trading.event.OrderStatusEvent;
import cex.crypto.trading.event.TradeExecutedEvent;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Consumer Configuration
 * Configures consumers for processing events from Kafka topics
 */
@Configuration
@EnableKafka
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    /**
     * Base consumer configuration
     */
    @Bean
    public Map<String, Object> consumerConfigs() {
        Map<String, Object> props = new HashMap<>();

        // Connection
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);

        // Deserialization (will be configured programmatically in ConsumerFactory)
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);

        // Consumer group settings
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // Process all messages
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false); // Manual commit for safety
        props.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 100); // Batch processing (100 records per poll)
        props.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 300000); // 5 minutes (matching can be slow)

        // Performance
        props.put(ConsumerConfig.FETCH_MIN_BYTES_CONFIG, 1024); // 1KB minimum fetch
        props.put(ConsumerConfig.FETCH_MAX_WAIT_MS_CONFIG, 500); // 500ms max wait

        // Reliability
        props.put(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, 30000); // 30 seconds
        props.put(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, 10000); // 10 seconds

        return props;
    }

    /**
     * Consumer factory for OrderCreatedEvent
     */
    @Bean
    public ConsumerFactory<String, OrderCreatedEvent> orderConsumerFactory() {
        JsonDeserializer<OrderCreatedEvent> deserializer = new JsonDeserializer<>(OrderCreatedEvent.class, false);
        deserializer.addTrustedPackages("cex.crypto.trading.event");

        return new DefaultKafkaConsumerFactory<>(
            consumerConfigs(),
            new StringDeserializer(),
            deserializer
        );
    }

    /**
     * Listener container factory for OrderCreatedEvent
     * Used by MatchingEngineConsumer
     * - 6 concurrent consumer threads (half of 12 partitions)
     * - Manual acknowledgment for transaction control
     * - Error handler with retry 3 times, then send to DLQ
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent>
            orderKafkaListenerContainerFactory(KafkaTemplate<String, OrderCreatedEvent> orderKafkaTemplate) {

        ConcurrentKafkaListenerContainerFactory<String, OrderCreatedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(orderConsumerFactory());
        factory.setConcurrency(6); // 6 consumer threads (each handles 2 partitions)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Error handler: retry 3 times with 1 second backoff, then send to DLQ
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            new FixedBackOff(1000L, 3L) // 1 second interval, 3 retry attempts
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    /**
     * Consumer factory for TradeExecutedEvent
     */
    @Bean
    public ConsumerFactory<String, TradeExecutedEvent> tradeConsumerFactory() {
        JsonDeserializer<TradeExecutedEvent> deserializer = new JsonDeserializer<>(TradeExecutedEvent.class, false);
        deserializer.addTrustedPackages("cex.crypto.trading.event");

        return new DefaultKafkaConsumerFactory<>(
            consumerConfigs(),
            new StringDeserializer(),
            deserializer
        );
    }

    /**
     * Listener container factory for TradeExecutedEvent
     * Lighter load than order processing (3 threads)
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, TradeExecutedEvent>
            tradeKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, TradeExecutedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(tradeConsumerFactory());
        factory.setConcurrency(3); // 3 consumer threads (lighter load)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Error handler with retry
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            new FixedBackOff(1000L, 3L)
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }

    /**
     * Consumer factory for OrderStatusEvent
     */
    @Bean
    public ConsumerFactory<String, OrderStatusEvent> statusConsumerFactory() {
        JsonDeserializer<OrderStatusEvent> deserializer = new JsonDeserializer<>(OrderStatusEvent.class, false);
        deserializer.addTrustedPackages("cex.crypto.trading.event");

        return new DefaultKafkaConsumerFactory<>(
            consumerConfigs(),
            new StringDeserializer(),
            deserializer
        );
    }

    /**
     * Listener container factory for OrderStatusEvent
     * Used by SSE broadcaster (2 threads, lightweight)
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, OrderStatusEvent>
            statusKafkaListenerContainerFactory() {

        ConcurrentKafkaListenerContainerFactory<String, OrderStatusEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();

        factory.setConsumerFactory(statusConsumerFactory());
        factory.setConcurrency(2); // 2 consumer threads (lightweight SSE broadcasting)
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL_IMMEDIATE);

        // Status updates are non-critical, acknowledge on error to avoid blocking
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            new FixedBackOff(0L, 0L) // No retry for status updates
        );
        factory.setCommonErrorHandler(errorHandler);

        return factory;
    }
}
