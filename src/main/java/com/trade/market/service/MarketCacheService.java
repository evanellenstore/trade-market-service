package com.trade.market.service;

import com.trade.market.dto.TickDto;

public interface MarketCacheService {
    void updateTick(TickDto tick);
    double getLatestPrice(String symbol);
    void clearCache();
}
