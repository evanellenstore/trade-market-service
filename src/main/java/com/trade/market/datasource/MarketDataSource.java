package com.trade.market.datasource;

import com.trade.market.entity.Candle;
import java.util.List;

public interface MarketDataSource {
    List<String> getSymbols();
    List<Candle> getCandles(String symbol, String timeframe, int limit);
}
