package com.trade.market.datasource;

import com.trade.market.cache.MarketCache;
import com.trade.market.entity.Candle;
import com.trade.market.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LiveMarketDataSource implements MarketDataSource {

    private final CandleRepository candleRepository;
    private final MarketCache marketCache;

    @Override
    public List<String> getSymbols() {
        return candleRepository.findDistinctSymbols();
    }

    @Override
    public List<Candle> getCandles(String symbol, String timeframe, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<Candle> candles = new ArrayList<>(
                candleRepository.findTop500BySymbolAndTimeframeOrderByCandleTimeDesc(symbol, timeframe));
        Candle current = marketCache.getCurrentCandle(symbol, timeframe);
        if (current != null) {
            candles.removeIf(candle -> current.getCandleTime().equals(candle.getCandleTime()));
            candles.add(current);
            candles.sort(Comparator.comparing(Candle::getCandleTime).reversed());
        }
        if (candles.size() <= limit) {
            return Collections.unmodifiableList(candles);
        }
        return Collections.unmodifiableList(candles.subList(0, limit));
    }
}
