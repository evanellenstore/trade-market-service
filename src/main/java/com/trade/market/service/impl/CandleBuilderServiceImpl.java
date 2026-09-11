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

        // Check if we need to finalize the current candle and start a new one

        if (current == null || isNewMinute(current, tick)) {
            if (current != null) {
                // Finalize the current candle and save it to the database
                finalizeCandle(current);
            }
            current = createNewCandle(tick);
            activeCandles.put(symbol, current);
        } else {
            double tradedPrice = tick.getLtp() > 0 ? tick.getLtp() : tick.getClose();
            current.setHigh(Math.max(current.getHigh(), tradedPrice));
            current.setLow(Math.min(current.getLow(), tradedPrice));
            current.setClose(tradedPrice);
            current.setVolume(current.getVolume() + tick.getVolume());
            current.setLtp(tick.getLtp());
        }

        marketCache.updateLatestPrice(symbol, tick.getLtp());
        marketCache.updateCurrentCandle(symbol, "ONE_MINUTE", current);
    }
    
    /**
     * Check if the incoming tick belongs to a new minute compared to the current candle
     * @param current
     * @param tick
     * @return
     */

    private boolean isNewMinute(Candle current, TickDto tick) {
        LocalDateTime currentTime = tick.getTimestamp();
        LocalDateTime candleStart = current.getStartTime();
        return currentTime.getMinute() != candleStart.getMinute()
                || currentTime.getHour() != candleStart.getHour()
                || currentTime.getDayOfYear() != candleStart.getDayOfYear();
    }

    /**
     * Create a new candle based on the incoming tick
     * @param tick
     * @return
     */

    private Candle createNewCandle(TickDto tick) {
        LocalDateTime startTime = tick.getTimestamp().withSecond(0).withNano(0);
        double tradedPrice = tick.getLtp() > 0 ? tick.getLtp() : tick.getClose();

        return Candle.builder()
                .symbol(tick.getSymbol())
                .symbolToken(tick.getToken())
                .exchange(tick.getExchange())
                .subscriptionId(tick.getSubscriptionId())
                .subscriptionName(tick.getSubscriptionName())
                .timeframe("ONE_MINUTE")
                .candleTime(startTime)
                .endTime(startTime.plusMinutes(1))
                .open(tradedPrice)
                .high(tradedPrice)
                .low(tradedPrice)
                .close(tradedPrice)
                .volume(tick.getVolume())
                .ltp(tick.getLtp())
                .build();
    }

    /**
     * Finalize the candle, save it to the database, update the market cache, and publish to Kafka
     * @param candle
     */

    private void finalizeCandle(Candle candle) {
        try {
            Candle saved = candleRepository.save(candle);
            log.debug("Finalized candle: {} at {}", saved.getSymbol(), saved.getStartTime());

            // Update market cache with the finalized candle
            marketCache.updateCurrentCandle(saved.getSymbol(), saved.getTimeframe(), saved);
           
           // Update BarSeriesManager with the finalized candle
            barSeriesManager.addCandle(saved.getSymbol(), saved.getTimeframe(), saved);
            
            // Aggregate finalized candle for higher timeframes
            candleAggregatorService.aggregateCandles(saved.getSymbol(), saved);

            // Publish finalized candle to Kafka
            kafkaProducerService.publishCandle(saved);
        } catch (Exception e) {
            log.error("Error finalizing candle", e);
        }
    }
}
