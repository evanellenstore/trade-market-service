package com.trade.market.snapshot.service;

import com.trade.market.dto.IndicatorResultDto;
import com.trade.market.entity.Candle;
import com.trade.market.kafka.MarketSnapshotProducer;
import com.trade.market.pattern.PatternResult;
import com.trade.market.snapshot.builder.MarketSnapshotBuilder;
import com.trade.market.snapshot.dto.MarketSnapshot;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketSnapshotService {
    private final MarketSnapshotBuilder snapshotBuilder;
    private final MarketSnapshotProducer snapshotProducer;

    public MarketSnapshot buildAndPublish(Candle candle, IndicatorResultDto indicators,
                                          PatternResult pattern, List<Candle> orderedCandles,
                                          boolean publish) {
        MarketSnapshot snapshot = snapshotBuilder.build(candle, indicators, pattern, orderedCandles);
        if (publish) {
            try {
                snapshotProducer.publish(snapshot);
            } catch (Exception e) {
                log.warn("Unable to publish market snapshot for {}", candle.getSymbol(), e);
            }
        }
        return snapshot;
    }
}