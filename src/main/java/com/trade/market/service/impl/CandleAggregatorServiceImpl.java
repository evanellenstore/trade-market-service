package com.trade.market.service.impl;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;

import com.trade.market.entity.Candle;
import com.trade.market.repository.CandleRepository;
import com.trade.market.service.CandleAggregatorService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class CandleAggregatorServiceImpl implements CandleAggregatorService {
    
    private final CandleRepository candleRepository;
    
    @Override
    public void aggregateCandles(String symbol, Candle oneMinuteCandle) {
        try {
            // Aggregate into 5-minute candles
            aggregateTo5Min(symbol, oneMinuteCandle);
            
            // Aggregate into 15-minute candles
            aggregateTo15Min(symbol, oneMinuteCandle);
            
            // Aggregate into 30-minute candles
            aggregateTo30Min(symbol, oneMinuteCandle);
            
            // Aggregate into 1-hour candles
            aggregateTo1Hour(symbol, oneMinuteCandle);
            
            // Aggregate into daily candles
            aggregateToDaily(symbol, oneMinuteCandle);
        } catch (Exception e) {
            log.error("Error aggregating candles for symbol: {}", symbol, e);
        }
    }
    
    @Override
    public List<Candle> getAggregatedCandles(String symbol, String timeframe, int limit) {
        return candleRepository.findBySymbolAndTimeframeOrderByCandleTimeDesc(symbol, timeframe)
                .stream()
                .limit(limit)
                .toList();
    }
    
    private void aggregateTo5Min(String symbol, Candle oneMinuteCandle) {
        LocalDateTime startTime = roundDownTo5Min(oneMinuteCandle.getStartTime());
        LocalDateTime endTime = startTime.plusMinutes(5);
        
        List<Candle> candles = candleRepository.findCandlesInTimeRange(
                symbol, "ONE_MINUTE", startTime, endTime);
        
        if (candles.size() >= 5) {
            Candle aggregated = buildAggregatedCandle(symbol, candles, "FIVE_MINUTE", startTime, endTime);
            candleRepository.save(aggregated);
            log.debug("Created 5-min candle for {} at {}", symbol, startTime);
        }
    }
    
    private void aggregateTo15Min(String symbol, Candle oneMinuteCandle) {
        LocalDateTime startTime = roundDownTo15Min(oneMinuteCandle.getStartTime());
        LocalDateTime endTime = startTime.plusMinutes(15);
        
        List<Candle> candles = candleRepository.findCandlesInTimeRange(
                symbol, "ONE_MINUTE", startTime, endTime);
        
        if (candles.size() >= 15) {
            Candle aggregated = buildAggregatedCandle(symbol, candles, "FIFTEEN_MINUTE", startTime, endTime);
            candleRepository.save(aggregated);
            log.debug("Created 15-min candle for {} at {}", symbol, startTime);
        }
    }
    
    private void aggregateTo30Min(String symbol, Candle oneMinuteCandle) {
        LocalDateTime startTime = roundDownTo30Min(oneMinuteCandle.getStartTime());
        LocalDateTime endTime = startTime.plusMinutes(30);
        
        List<Candle> candles = candleRepository.findCandlesInTimeRange(
                symbol, "ONE_MINUTE", startTime, endTime);
        
        if (candles.size() >= 30) {
            Candle aggregated = buildAggregatedCandle(symbol, candles, "THIRTY_MINUTE", startTime, endTime);
            candleRepository.save(aggregated);
            log.debug("Created 30-min candle for {} at {}", symbol, startTime);
        }
    }
    
    private void aggregateTo1Hour(String symbol, Candle oneMinuteCandle) {
        LocalDateTime startTime = roundDownToHour(oneMinuteCandle.getStartTime());
        LocalDateTime endTime = startTime.plusHours(1);
        
        List<Candle> candles = candleRepository.findCandlesInTimeRange(
                symbol, "ONE_MINUTE", startTime, endTime);
        
        if (candles.size() >= 60) {
            Candle aggregated = buildAggregatedCandle(symbol, candles, "ONE_HOUR", startTime, endTime);
            candleRepository.save(aggregated);
            log.debug("Created 1-hour candle for {} at {}", symbol, startTime);
        }
    }
    
    private void aggregateToDaily(String symbol, Candle oneMinuteCandle) {
        LocalDateTime startTime = roundDownToDay(oneMinuteCandle.getStartTime());
        LocalDateTime endTime = startTime.plusDays(1);
        
        List<Candle> candles = candleRepository.findCandlesInTimeRange(
                symbol, "ONE_MINUTE", startTime, endTime);
        
        if (candles.size() >= 390) { // Approximate trading minutes in a day
            Candle aggregated = buildAggregatedCandle(symbol, candles, "DAILY", startTime, endTime);
            candleRepository.save(aggregated);
            log.debug("Created daily candle for {} at {}", symbol, startTime);
        }
    }
    
    private Candle buildAggregatedCandle(String symbol, List<Candle> candles, String timeframe,
            LocalDateTime startTime, LocalDateTime endTime) {
        candles.sort(Comparator.comparing(Candle::getStartTime));

        Candle lastCandle = candles.get(candles.size() - 1);

        return Candle.builder()
                .symbol(symbol)
                .symbolToken(lastCandle.getSymbolToken())
                .exchange(lastCandle.getExchange())
                .timeframe(timeframe)
                .candleTime(startTime)
                .endTime(endTime)
                .open(candles.get(0).getOpen())
                .high(candles.stream().mapToDouble(Candle::getHigh).max().orElse(0.0))
                .low(candles.stream().mapToDouble(Candle::getLow).min().orElse(0.0))
                .close(lastCandle.getClose())
                .ltp(lastCandle.getLtp()) // <-- latest traded price
                .volume(lastCandle.getVolume()) // <-- total volume of the last candle in the aggregation
                .build();
    }
    
    private LocalDateTime roundDownTo5Min(LocalDateTime time) {
        return time.withMinute((time.getMinute() / 5) * 5).withSecond(0).withNano(0);
    }
    
    private LocalDateTime roundDownTo15Min(LocalDateTime time) {
        return time.withMinute((time.getMinute() / 15) * 15).withSecond(0).withNano(0);
    }
    
    private LocalDateTime roundDownTo30Min(LocalDateTime time) {
        return time.withMinute((time.getMinute() / 30) * 30).withSecond(0).withNano(0);
    }
    
    private LocalDateTime roundDownToHour(LocalDateTime time) {
        return time.withMinute(0).withSecond(0).withNano(0);
    }
    
    private LocalDateTime roundDownToDay(LocalDateTime time) {
        return time.withHour(9).withMinute(0).withSecond(0).withNano(0); // Market opens at 9 AM
    }
}
