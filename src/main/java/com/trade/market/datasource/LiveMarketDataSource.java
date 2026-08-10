package com.trade.market.datasource;

import com.trade.market.entity.Candle;
import com.trade.market.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
public class LiveMarketDataSource implements MarketDataSource {

    private final CandleRepository candleRepository;

    @Override
    public List<String> getSymbols() {
        return candleRepository.findDistinctSymbols();
    }

    @Override
    public List<Candle> getCandles(String symbol, String timeframe, int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<Candle> candles = candleRepository.findTop500BySymbolAndTimeframeOrderByCandleTimeDesc(symbol, timeframe);
        if (candles.size() <= limit) {
            return candles;
        }
        return Collections.unmodifiableList(candles.subList(0, limit));
    }
}
