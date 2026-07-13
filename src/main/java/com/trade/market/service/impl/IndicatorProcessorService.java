package com.trade.market.service.impl;

import com.trade.market.dto.IndicatorResultDto;
import com.trade.market.indicator.BarSeriesManager;
import com.trade.market.kafka.KafkaProducerService;
import com.trade.market.pattern.PatternEngine;
import com.trade.market.repository.CandleRepository;
import com.trade.market.service.IndicatorPersistenceService;
import com.trade.market.service.IndicatorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

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
    private final BarSeriesManager barSeriesManager;

    @Scheduled(fixedDelayString = "${market.scheduler.indicator-delay-ms:60000}")
    public void processLatestIndicators() {
        List<String> symbols = candleRepository.findAll().stream().map(c -> c.getSymbol()).distinct().collect(Collectors.toList());
        for (String symbol : symbols) {
            try {
                List<com.trade.market.entity.Candle> candles = candleRepository
                        .findTop500BySymbolAndTimeframeOrderByStartTimeDesc(symbol, "ONE_MINUTE");
                if (candles.isEmpty()) {
                    continue;
                }
                List<Double> closes = candles.stream().map(com.trade.market.entity.Candle::getClose).collect(Collectors.toList());
                IndicatorResultDto result = indicatorService.calculateIndicators(symbol, "ONE_MINUTE", closes);
                indicatorPersistenceService.save(result);
                kafkaProducerService.publishIndicator(symbol, result);
                barSeriesManager.addCandle(symbol, "ONE_MINUTE", candles.get(0));
                String pattern = patternEngine.detectPattern(symbol, closes);
                if (!"NONE".equals(pattern)) {
                    kafkaProducerService.publishPattern(symbol, pattern);
                }
            } catch (Exception e) {
                log.warn("Unable to process indicators for {}", symbol, e);
            }
        }
    }
}
