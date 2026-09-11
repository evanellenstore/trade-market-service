package com.trade.market.cache;

import com.trade.market.entity.Candle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Duration;
import java.time.Instant;

@Component
@Slf4j
public class MarketCache {

    private final Map<String, Double> latestPriceMap = new ConcurrentHashMap<>();
    private final Map<String, Instant> latestTickTimeMap = new ConcurrentHashMap<>();
    private final Map<String, List<Double>> barSeriesMap = new ConcurrentHashMap<>();
    private final Map<String, Map<String, Candle>> currentCandleMap = new ConcurrentHashMap<>();

    public void updateLatestPrice(String symbol, Double price) {
        latestPriceMap.put(symbol, price);
        latestTickTimeMap.put(symbol, Instant.now());
    }

    public List<String> getRecentlyUpdatedSymbols(Duration maxAge) {
        Instant cutoff = Instant.now().minus(maxAge);
        return latestTickTimeMap.entrySet().stream()
                .filter(entry -> entry.getValue().isAfter(cutoff))
                .map(Map.Entry::getKey)
                .toList();
    }

    public Double getLatestPrice(String symbol) {
        return latestPriceMap.getOrDefault(symbol, 0.0);
    }

    public void updateBarSeries(String symbol, String timeframe, List<Double> closePrices) {
        barSeriesMap.put(buildKey(symbol, timeframe), closePrices);
    }

    public List<Double> getBarSeries(String symbol, String timeframe) {
        return barSeriesMap.get(buildKey(symbol, timeframe));
    }

    public void updateCurrentCandle(String symbol, String timeframe, Candle candle) {
        currentCandleMap.computeIfAbsent(symbol, ignored -> new ConcurrentHashMap<>()).put(timeframe, candle);
    }

    public Candle getCurrentCandle(String symbol, String timeframe) {
        Map<String, Candle> candlesByTimeframe = currentCandleMap.get(symbol);
        if (candlesByTimeframe == null) {
            return null;
        }
        return candlesByTimeframe.get(timeframe);
    }

    public Map<String, List<Double>> getAllBarSeries() {
        return new HashMap<>(barSeriesMap);
    }

    public void clearCache() {
        latestPriceMap.clear();
        latestTickTimeMap.clear();
        barSeriesMap.clear();
        currentCandleMap.clear();
        log.info("Market cache cleared");
    }

    private String buildKey(String symbol, String timeframe) {
        return symbol + ":" + timeframe;
    }
}
