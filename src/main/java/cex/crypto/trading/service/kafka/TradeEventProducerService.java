package cex.crypto.trading.service.kafka;

import cex.crypto.trading.domain.Trade;
import cex.crypto.trading.event.TradeExecutedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Producer service for TradeExecutedEvent
 * Publishes executed trades to trade-output topic
 */
@Slf4j
@Service
public class TradeEventProducerService {

    @Autowired
    private KafkaTemplate<String, TradeExecutedEvent> tradeKafkaTemplate;

    @Value("${kafka.topics.trade-output}")
    private String tradeOutputTopic;

    /**
     * Publish trade event to Kafka
     * Partition key = symbol (ensures same trading pair goes to same partition)
     *
     * @param trade the trade to publish
     * @param takerOrderId the order ID that triggered the match
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, TradeExecutedEvent>> publishTrade(
            Trade trade, Long takerOrderId) {

        // Create event from trade
        TradeExecutedEvent event = TradeExecutedEvent.fromTrade(trade, takerOrderId);

        // Partition by symbol
        String partitionKey = trade.getSymbol();

        log.info("Publishing trade to Kafka: tradeId={}, symbol={}, buyOrderId={}, sellOrderId={}, price={}, quantity={}",
                trade.getTradeId(), trade.getSymbol(), trade.getBuyOrderId(),
                trade.getSellOrderId(), trade.getPrice(), trade.getQuantity());

        // Send to Kafka asynchronously
        CompletableFuture<SendResult<String, TradeExecutedEvent>> future =
                tradeKafkaTemplate.send(tradeOutputTopic, partitionKey, event);

        // Add callback handlers
        future.whenComplete((result, ex) -> {
            if (ex == null) {
                // Success
                log.info("Trade published successfully: tradeId={}, partition={}, offset={}",
                        trade.getTradeId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                // Failure - log error (trades are already persisted in DB)
                log.error("Failed to publish trade: tradeId={}, error={}",
                        trade.getTradeId(), ex.getMessage(), ex);
            }
        });

        return future;
    }

    /**
     * Publish trade event (alternative signature using TradeExecutedEvent directly)
     *
     * @param event the trade event to publish
     * @return CompletableFuture with send result
     */
    public CompletableFuture<SendResult<String, TradeExecutedEvent>> publishTradeEvent(
            TradeExecutedEvent event) {

        String partitionKey = event.getSymbol();

        log.info("Publishing trade event to Kafka: tradeId={}, symbol={}, messageId={}",
                event.getTradeId(), event.getSymbol(), event.getMessageId());

        CompletableFuture<SendResult<String, TradeExecutedEvent>> future =
                tradeKafkaTemplate.send(tradeOutputTopic, partitionKey, event);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Trade event published successfully: tradeId={}, partition={}, offset={}",
                        event.getTradeId(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to publish trade event: tradeId={}, error={}",
                        event.getTradeId(), ex.getMessage(), ex);
            }
        });

        return future;
    }
}
