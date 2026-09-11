package com.trade.market.service;

import com.trade.market.dto.TickDto;
import java.time.Duration;
import java.util.List;

public interface MarketCacheService {
    void updateTick(TickDto tick);
    double getLatestPrice(String symbol);
    List<String> getRecentlyUpdatedSymbols(Duration maxAge);
    void clearCache();
}
