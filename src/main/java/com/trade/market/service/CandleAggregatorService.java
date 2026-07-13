package com.trade.market.service;

import com.trade.market.entity.Candle;
import java.util.List;

/**
 * Service for aggregating 1-minute candles into higher timeframes
 */
public interface CandleAggregatorService {
    
    /**
     * Aggregate completed 1-minute candles into higher timeframes
     */
    void aggregateCandles(String symbol, Candle oneMinuteCandle);
    
    /**
     * Get aggregated candles for a timeframe
     */
    List<Candle> getAggregatedCandles(String symbol, String timeframe, int limit);
}
