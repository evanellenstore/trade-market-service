package com.trade.market.job;

import com.trade.market.dto.IndicatorResultDto;
import com.trade.market.indicator.BarSeriesManager;
import com.trade.market.kafka.KafkaProducerService;
import com.trade.market.pattern.PatternEngine;
import com.trade.market.pattern.PatternResult;
import com.trade.market.repository.CandleRepository;
import com.trade.market.service.IndicatorPersistenceService;
import com.trade.market.service.IndicatorService;
import com.trade.market.service.PatternPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndicatorProcessorService {

    private final CandleRepository candleRepository;
    private final IndicatorService indicatorService;
    private final PatternEngine patternEngine;
    private final KafkaProducerService kafkaProducerService;
    private final IndicatorPersistenceService indicatorPersistenceService;
    private final PatternPersistenceService patternPersistenceService;
    private final BarSeriesManager barSeriesManager;

    /* 
     * This method is scheduled to run at a fixed delay (default 60 seconds) to process the latest indicators for all symbols.
     * It retrieves the latest candles for each symbol, calculates indicators, detects patterns, and publishes results to Kafka.
     */

    @Scheduled(fixedDelayString = "${market.scheduler.indicator-delay-ms:60000}")
    public void processLatestIndicators() {
        List<String> symbols = candleRepository.findDistinctSymbols();
        for (String symbol : symbols) {
            try {
                List<com.trade.market.entity.Candle> candles = candleRepository
                        .findTop500BySymbolAndTimeframeOrderByCandleTimeDesc(symbol, "ONE_MINUTE");
                if (candles.isEmpty()) {
                    continue;
                }

                // Prepare closes for indicator calculations (ordered newest->oldest by repo query)
                List<Double> closes = candles.stream().map(com.trade.market.entity.Candle::getClose).collect(Collectors.toList());

                // Ensure the TA4J series is initialized in chronological order before calculating indicators.
                // If the series already exists, only add the newest candle to keep the sequence current.
                List<com.trade.market.entity.Candle> orderedCandles = candles.stream()
                        .sorted(Comparator.comparing(com.trade.market.entity.Candle::getCandleTime))
                        .collect(Collectors.toList());

                if (barSeriesManager.getSeries(symbol, "ONE_MINUTE") == null) {
                    for (com.trade.market.entity.Candle candle : orderedCandles) {
                        barSeriesManager.addCandle(symbol, "ONE_MINUTE", candle);
                    }
                } else {
                    barSeriesManager.addCandle(symbol, "ONE_MINUTE", candles.get(0));
                }

                IndicatorResultDto result = indicatorService.calculateIndicators(symbol, "ONE_MINUTE", candles.get(0).getSymbolToken(), candles.get(0).getCandleTime(), closes);
                indicatorPersistenceService.save(result);

                // Publish the indicator result to Kafka without aborting the pattern flow if Kafka is unavailable.
                try {
                    kafkaProducerService.publishIndicator(symbol, result);
                } catch (Exception e) {
                    log.warn("Unable to publish indicator update for {}", symbol, e);
                }

                // Convert entity candles to domain candles and detect pattern.
                // The pattern engine expects chronological order (oldest -> newest), while the repository query returns newest first.
                List<com.trade.market.pattern.Candle> domainCandles = orderedCandles.stream()
                        .map(ec -> {
                            long startTime = ec.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
                            return com.trade.market.pattern.Candle.builder()
                                    .symbol(symbol)
                                    .open(ec.getOpen())
                                    .high(ec.getHigh())
                                    .low(ec.getLow())
                                    .close(ec.getClose())
                                    .volume((long) ec.getVolume())
                                    .startTime(startTime)
                                    .endTime(startTime + 60_000L)
                                    .build();
                        })
                        .collect(Collectors.toList());

                PatternResult patternResult = PatternResult.none();
                if (!domainCandles.isEmpty()) {
                    try {
                        patternResult = patternEngine.detectPattern(symbol, domainCandles);
                        log.info("Pattern processing result for {}: detected={} pattern={} confidence={}",
                                symbol,
                                patternResult.isPatternDetected(),
                                patternResult.getPattern() != null ? patternResult.getPattern().name() : "NONE",
                                patternResult.getConfidence());
                    } catch (Exception e) {
                        log.warn("Unable to detect pattern for {}", symbol, e);
                    }
                } else {
                    log.info("No candles available for pattern detection for {}", symbol);
                }

                patternPersistenceService.save(symbol, candles.get(0).getSymbolToken(), "ONE_MINUTE",
                        candles.get(0).getCandleTime(), patternResult);

                // If a pattern is detected, publish it to Kafka without aborting the rest of the flow.
                if (patternResult.isPatternDetected()) {
                    try {
                        kafkaProducerService.publishPattern(symbol, patternResult.getPattern().name());
                    } catch (Exception e) {
                        log.warn("Unable to publish pattern update for {}", symbol, e);
                    }
                }
            } catch (Exception e) {
                log.warn("Unable to process indicators for {}", symbol, e);
            }
        }
    }
}
