package com.trade.market.snapshot.producer;

import com.trade.market.snapshot.dto.MarketSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketSnapshotProducer {
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${market.kafka.topics.snapshot:market.snapshot}")
    private String snapshotTopic;

    public void publish(MarketSnapshot snapshot) {
        kafkaTemplate.send(snapshotTopic, snapshot.getSymbol(), snapshot)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("Unable to publish market snapshot for {} to {}", snapshot.getSymbol(), snapshotTopic, error);
                    } else {
                        log.debug("Published market snapshot for {} to {}", snapshot.getSymbol(), snapshotTopic);
                    }
                });
    }
}