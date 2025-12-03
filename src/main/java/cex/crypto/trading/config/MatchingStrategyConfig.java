package cex.crypto.trading.config;

import cex.crypto.trading.enums.OrderType;
import cex.crypto.trading.strategy.LimitOrderMatchingStrategy;
import cex.crypto.trading.strategy.MarketOrderMatchingStrategy;
import cex.crypto.trading.strategy.OrderMatchingStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

import java.util.Map;

/**
 * Configuration for order matching strategies and retry behavior
 */
@Configuration
@EnableRetry
public class MatchingStrategyConfig {

    /**
     * Create a map of OrderType to OrderMatchingStrategy for strategy pattern injection
     * This allows MatchingEngineService to select the appropriate strategy based on order type
     *
     * @param limitStrategy the LIMIT order matching strategy
     * @param marketStrategy the MARKET order matching strategy
     * @return map of order type to strategy
     */
    @Bean
    public Map<OrderType, OrderMatchingStrategy> matchingStrategies(
            LimitOrderMatchingStrategy limitStrategy,
            MarketOrderMatchingStrategy marketStrategy) {
        return Map.of(
                OrderType.LIMIT, limitStrategy,
                OrderType.MARKET, marketStrategy
        );
    }
}
