package com.trade.market.service.impl;

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

                // Ensure TA4J series is initialized oldest->newest before calculating indicators
                if (barSeriesManager.getSeries(symbol, "ONE_MINUTE") == null) {
                    for (int i = candles.size() - 1; i >= 0; i--) {
                        barSeriesManager.addCandle(symbol, "ONE_MINUTE", candles.get(i));
                    }
                }

                IndicatorResultDto result = indicatorService.calculateIndicators(symbol, "ONE_MINUTE", candles.get(0).getSymbolToken(), candles.get(0).getCandleTime(), closes);
                indicatorPersistenceService.save(result);
                kafkaProducerService.publishIndicator(symbol, result);

                // Series exists — try adding only the latest candle (newest)
                barSeriesManager.addCandle(symbol, "ONE_MINUTE", candles.get(0));

                // Convert entity candles to domain candles and detect pattern
                List<com.trade.market.pattern.Candle> domainCandles = candles.stream()
                        .map(ec -> com.trade.market.pattern.Candle.builder()
                                .symbol(symbol)
                                .open(ec.getOpen())
                                .high(ec.getHigh())
                                .low(ec.getLow())
                                .close(ec.getClose())
                                .volume((long) ec.getVolume())
                                .startTime(ec.getStartTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli())
                                .build())
                        .collect(Collectors.toList());
                
                PatternResult patternResult = patternEngine.detectPattern(symbol, domainCandles);


                patternPersistenceService.save(symbol, candles.get(0).getSymbolToken(), "ONE_MINUTE",
                        candles.get(0).getCandleTime(), patternResult);

                if (patternResult.isPatternDetected()) {
                    kafkaProducerService.publishPattern(symbol, patternResult.getPattern().name());
                }
            } catch (Exception e) {
                log.warn("Unable to process indicators for {}", symbol, e);
            }
        }
    }
}
