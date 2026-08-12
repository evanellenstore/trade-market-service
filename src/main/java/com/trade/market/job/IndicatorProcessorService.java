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
import com.trade.market.repository.CandleRepository;
import com.trade.market.service.BrokerTokenModeService;
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

/**
 * Service responsible for processing market indicators and detecting patterns.
 * <p>
 * This service supports both live indicator processing and scheduled backtest runs.
 * It delegates indicator calculations, persistence, pattern detection, and Kafka publishing.
 */
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
    private final BrokerTokenModeService brokerTokenModeService;
    private final CandleRepository candleRepository;

    /**
     * Scheduled task that processes the latest indicators for live mode.
     * <p>
     * If the broker token mode is live, this method retrieves symbols and candles from the live
     * data source and processes each symbol. If the broker token mode is backtest, it runs a
     * scheduled backtest for all available symbols using historical candle data.
     */
    @Scheduled(fixedDelayString = "${market.scheduler.indicator-delay-ms:60000}")
    public void processLatestIndicatorsLive() {
        boolean isLive;
        try {
            isLive = brokerTokenModeService.isLiveMode();
        } catch (Exception e) {
            log.warn("Unable to determine broker token mode, defaulting to live processing", e);
            isLive = true;
        }

        if (isLive) {
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
        } else {
            log.info("Broker token mode set to backtest - running scheduled backtest for available symbols");
            List<String> symbols = candleRepository.findDistinctSymbols();
            String runId = "scheduled-backtest-" + System.currentTimeMillis();
            for (String symbol : symbols) {
                try {
                    List<Candle> history = candleRepository.findBySymbolAndTimeframeOrderByCandleTimeDesc(symbol,
                            ONE_MINUTE);
                    if (history == null || history.isEmpty()) {
                        continue;
                    }
                    // repository returns desc order, reverse to oldest-first
                    java.util.Collections.reverse(history);
                    runBacktest(symbol, history, runId);
                } catch (Exception e) {
                    log.warn("Unable to run scheduled backtest for {}", symbol, e);
                }
            }
        }
    }

    /**
     * Executes a backtest for the given symbol over the provided candle history.
     *
     * @param symbol              the market symbol to backtest
     * @param historyOldestFirst  the candle history ordered from oldest to newest
     * @param runId               the unique backtest run identifier
     * @param mode                the processing mode that must represent a backtest configuration
     * @throws IllegalArgumentException if the mode is null, live, or the runId does not match the mode
     */
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

        BacktestMarketDataSource backtestMarketDataSource = new BacktestMarketDataSource(symbol, ONE_MINUTE,
                historyOldestFirst);
        while (backtestMarketDataSource.hasNext()) {
            List<Candle> candles = backtestMarketDataSource.advance(DEFAULT_CANDLE_LIMIT);
            processSymbol(symbol, candles, mode);
        }
    }

    /**
     * Starts a backtest run using the configured backtest properties.
     *
     * @param symbol             the market symbol to backtest
     * @param historyOldestFirst the candle history ordered from oldest to newest
     * @param runId              the unique backtest run identifier
     */
    public void runBacktest(String symbol, List<Candle> historyOldestFirst, String runId) {
        ProcessingMode mode = ProcessingMode.backtest(runId, backtestProperties.isPersist(),
                backtestProperties.isPublish());
        runBacktest(symbol, historyOldestFirst, runId, mode);
    }

    /**
     * Processes a stream of candles for a symbol and calculates indicators and patterns.
     * <p>
     * This method updates the bar series, calculates indicator values, optionally persists
     * results, publishes updates to Kafka, and detects chart patterns.
     *
     * @param symbol  the market symbol being processed
     * @param candles the list of candles to process
     * @param mode    the processing mode that controls persistence and publishing behavior
     */
    public void processSymbol(String symbol, List<Candle> candles, ProcessingMode mode) {
        if (candles == null || candles.isEmpty()) {
            return;
        }

        List<Candle> orderedCandles = candles.stream()
                .sorted(Comparator.comparing(Candle::getCandleTime))
                .collect(Collectors.toList());
        List<Double> closes = orderedCandles.stream()
                .map(Candle::getClose)
                .collect(Collectors.toList());

        String seriesKey = mode.isLive() ? symbol : symbol + "::" + mode.getRunId();
        Candle latestCandle = orderedCandles.get(orderedCandles.size() - 1);

        processIndicator(symbol, closes, orderedCandles, mode, seriesKey, latestCandle);
        processPattern(symbol, orderedCandles, mode, latestCandle);
    }

    /**
     * Processes indicator calculations for a symbol based on the provided candle data.
     * @param symbol
     * @param closes
     * @param orderedCandles
     * @param mode
     * @param seriesKey
     * @param latestCandle
     */
    private void processIndicator(String symbol, List<Double> closes, List<Candle> orderedCandles,
                                  ProcessingMode mode, String seriesKey, Candle latestCandle) {
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
    }

    /**
     * Processes pattern detection for a symbol based on the provided candle data.
     *
     * @param symbol
     * @param orderedCandles
     * @param mode
     * @param latestCandle
     */
    private void processPattern(String symbol, List<Candle> orderedCandles, ProcessingMode mode,
                                Candle latestCandle) {
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
                log.debug("Detecting pattern for {} with {} candles", symbol, domainCandles.size());
                patternResult = patternEngine.detectPattern(symbol, domainCandles);
                if (patternResult == null) {
                    patternResult = PatternResult.none();
                }
                log.info("Pattern processing result for {}: detected={} pattern={} confidence={}",
                        symbol,
                        patternResult.isPatternDetected(),
                        patternResult.getPattern() != null ? patternResult.getPattern().name() : "NONE",
                        patternResult.getConfidence());
            } catch (Exception e) {
                log.warn("Unable to detect pattern for {}", symbol, e);
                patternResult = PatternResult.none();
            }
        } else {
            log.info("No candles available for pattern detection for {}", symbol);
        }

        if (mode.isPersist()) {
            patternPersistenceService.save(symbol, latestCandle.getSymbolToken(), ONE_MINUTE,
                    mode.getRunId(), latestCandle.getCandleTime(), patternResult);
        }

        if (mode.isPublish()) {
            try {
                String patternName = patternResult.isPatternDetected()
                        ? patternResult.getPattern().name()
                        : "NoPatternDetected";
                kafkaProducerService.publishPattern(symbol, patternName);
            } catch (Exception e) {
                log.warn("Unable to publish pattern update for {}", symbol, e);
            }
        }

        if (!patternResult.isPatternDetected()) {
            System.out.println("============= ********** No pattern detected for " + symbol);
        }
    }
}
