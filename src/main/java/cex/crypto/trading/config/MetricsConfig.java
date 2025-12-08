package cex.crypto.trading.config;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Metrics configuration for Prometheus monitoring
 *
 * Configures:
 * - Common metric tags for all metrics
 * - MeterRegistry customization
 */
@Slf4j
@Configuration
public class MetricsConfig {

    /**
     * Common tags for all metrics
     * These will be added to every metric published
     */
    @Bean
    public List<Tag> commonTags() {
        return List.of(
            Tag.of("service", "trading-engine"),
            Tag.of("component", "kafka-consumers")
        );
    }

    /**
     * MeterRegistry customizer to add common tags
     * Executed automatically by Spring Boot
     */
    @Bean
    public MeterBinder commonTagsBinder(List<Tag> commonTags) {
        return (MeterRegistry registry) -> {
            registry.config().commonTags(commonTags);
            log.info("Registered common metric tags: {}", commonTags);
        };
    }
}
