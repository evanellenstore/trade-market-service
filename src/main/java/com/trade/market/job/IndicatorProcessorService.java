package com.trade.market.job;

import com.trade.market.config.BacktestProperties;
import com.trade.market.datasource.BacktestMarketDataSource;
import com.trade.market.datasource.LiveMarketDataSource;
import com.trade.market.dto.IndicatorResultDto;
import com.trade.market.entity.Candle;
import com.trade.market.indicator.BarSeriesManager;
import com.trade.market.kafka.KafkaProducerService;
import com.trade.market.model.ProcessingMode;
import com.trade.market.pattern.PatternEngine;
import com.trade.market.pattern.PatternResult;
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

    private static final String ONE_MINUTE = "ONE_MINUTE";
    private static final int DEFAULT_CANDLE_LIMIT = 500;

    private final LiveMarketDataSource liveMarketDataSource;
    private final IndicatorService indicatorService;
    private final PatternEngine patternEngine;
    private final KafkaProducerService kafkaProducerService;
    private final IndicatorPersistenceService indicatorPersistenceService;
    private final PatternPersistenceService patternPersistenceService;
    private final BarSeriesManager barSeriesManager;
    private final BacktestProperties backtestProperties;

    @Scheduled(fixedDelayString = "${market.scheduler.indicator-delay-ms:60000}")
    public void processLatestIndicatorsLive() {
        List<String> symbols = liveMarketDataSource.getSymbols();
        for (String symbol : symbols) {
            try {
                List<Candle> candles = liveMarketDataSource.getCandles(symbol, ONE_MINUTE, DEFAULT_CANDLE_LIMIT);
                if (candles.isEmpty()) {
                    continue;
                }
                processSymbol(symbol, candles, ProcessingMode.live());
            } catch (Exception e) {
                log.warn("Unable to process indicators for {}", symbol, e);
            }
        }
    }

    public void runBacktest(String symbol, List<Candle> historyOldestFirst, String runId, ProcessingMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("ProcessingMode must not be null for backtest");
        }
        if (mode.isLive()) {
            throw new IllegalArgumentException("Backtest mode must not be live");
        }
        if (runId == null || !runId.equals(mode.getRunId())) {
            throw new IllegalArgumentException("runId must match the ProcessingMode runId for backtest");
        }

        BacktestMarketDataSource backtestMarketDataSource = new BacktestMarketDataSource(symbol, ONE_MINUTE, historyOldestFirst);
        while (backtestMarketDataSource.hasNext()) {
            List<Candle> candles = backtestMarketDataSource.advance(DEFAULT_CANDLE_LIMIT);
            processSymbol(symbol, candles, mode);
        }
    }

    public void runBacktest(String symbol, List<Candle> historyOldestFirst, String runId) {
        ProcessingMode mode = ProcessingMode.backtest(runId, backtestProperties.isPersist(), backtestProperties.isPublish());
        runBacktest(symbol, historyOldestFirst, runId, mode);
    }

    public void processSymbol(String symbol, List<Candle> candles, ProcessingMode mode) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        List<Double> closes = candles.stream().map(Candle::getClose).collect(Collectors.toList());
        List<Candle> orderedCandles = candles.stream()
                .sorted(Comparator.comparing(Candle::getCandleTime))
                .collect(Collectors.toList());

        String seriesKey = mode.isLive() ? symbol : symbol + "::" + mode.getRunId();
        Candle latestCandle = orderedCandles.get(orderedCandles.size() - 1);

        if (barSeriesManager.getSeriesByKey(seriesKey, ONE_MINUTE) == null) {
            for (Candle candle : orderedCandles) {
                barSeriesManager.addCandleByKey(seriesKey, ONE_MINUTE, candle);
            }
        } else {
            barSeriesManager.addCandleByKey(seriesKey, ONE_MINUTE, latestCandle);
        }

        IndicatorResultDto result = indicatorService.calculateIndicatorsBySeriesKey(symbol, ONE_MINUTE, seriesKey,
                latestCandle.getSymbolToken(), latestCandle.getCandleTime(), closes);
        result.setRunId(mode.getRunId());

        if (mode.isPersist()) {
            indicatorPersistenceService.save(result);
        }

        if (mode.isPublish()) {
            try {
                kafkaProducerService.publishIndicator(symbol, result);
            } catch (Exception e) {
                log.warn("Unable to publish indicator update for {}", symbol, e);
            }
        }

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

        if (mode.isPersist()) {
            patternPersistenceService.save(symbol, latestCandle.getSymbolToken(), ONE_MINUTE,
                    mode.getRunId(), latestCandle.getCandleTime(), patternResult);
        }

        if (patternResult.isPatternDetected() && mode.isPublish()) {
            try {
                kafkaProducerService.publishPattern(symbol, patternResult.getPattern().name());
            } catch (Exception e) {
                log.warn("Unable to publish pattern update for {}", symbol, e);
            }
        }
    }
}
