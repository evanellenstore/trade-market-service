package com.trade.market.service.impl;

import com.trade.market.cache.MarketCache;
import com.trade.market.dto.TickDto;
import com.trade.market.entity.Candle;
import com.trade.market.indicator.BarSeriesManager;
import com.trade.market.kafka.KafkaProducerService;
import com.trade.market.repository.CandleRepository;
import com.trade.market.service.CandleAggregatorService;
import com.trade.market.service.CandleBuilderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class CandleBuilderServiceImpl implements CandleBuilderService {

    private final MarketCache marketCache;
    private final CandleRepository candleRepository;
    private final CandleAggregatorService candleAggregatorService;
    private final KafkaProducerService kafkaProducerService;
    private final BarSeriesManager barSeriesManager;

    private final Map<String, Candle> activeCandles = new ConcurrentHashMap<>();

    @Override
    public void processTick(TickDto tick) {
        if (tick == null ) {
            return;
        }

        String symbol = tick.getSymbol();
        Candle current = activeCandles.get(symbol);

        if (current == null || isNewMinute(current, tick)) {
            if (current != null) {
                finalizeCandle(current);
            }
            current = createNewCandle(tick);
            activeCandles.put(symbol, current);
        } else {
            current.setHigh(Math.max(current.getHigh(), tick.getHigh()));
            current.setLow(Math.min(current.getLow(), tick.getLow()));
            current.setClose(tick.getClose());
            current.setVolume(current.getVolume() + tick.getVolume());
            current.setLtp(tick.getLtp());
        }

        marketCache.updateLatestPrice(symbol, tick.getLtp());
        marketCache.updateCurrentCandle(symbol, "ONE_MINUTE", current);
    }

    private boolean isNewMinute(Candle current, TickDto tick) {
        LocalDateTime currentTime = tick.getTimestamp();
        LocalDateTime candleStart = current.getStartTime();
        return currentTime.getMinute() != candleStart.getMinute()
                || currentTime.getHour() != candleStart.getHour()
                || currentTime.getDayOfYear() != candleStart.getDayOfYear();
    }

    private Candle createNewCandle(TickDto tick) {
        LocalDateTime startTime = tick.getTimestamp().withSecond(0).withNano(0);

        return Candle.builder()
                .symbol(tick.getSymbol())
                .symbolToken(tick.getToken())
                .exchange(tick.getExchange())
                .timeframe("ONE_MINUTE")
                .candleTime(startTime)
                .endTime(startTime.plusMinutes(1))
                .open(tick.getOpen())
                .high(tick.getHigh())
                .low(tick.getLow())
                .close(tick.getClose())
                .volume(tick.getVolume())
                .ltp(tick.getLtp())
                .build();
    }

    private void finalizeCandle(Candle candle) {
        try {
            Candle saved = candleRepository.save(candle);
            log.debug("Finalized candle: {} at {}", saved.getSymbol(), saved.getStartTime());
            marketCache.updateCurrentCandle(saved.getSymbol(), saved.getTimeframe(), saved);
            barSeriesManager.addCandle(saved.getSymbol(), saved.getTimeframe(), saved);
            candleAggregatorService.aggregateCandles(saved.getSymbol(), saved);
            kafkaProducerService.publishCandle(saved);
        } catch (Exception e) {
            log.error("Error finalizing candle", e);
        }
    }
}
