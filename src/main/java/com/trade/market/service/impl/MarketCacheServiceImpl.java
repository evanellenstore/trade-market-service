package com.trade.market.service.impl;

import com.trade.market.cache.MarketCache;
import com.trade.market.dto.TickDto;
import com.trade.market.service.MarketCacheService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.time.Duration;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketCacheServiceImpl implements MarketCacheService {
    
    private final MarketCache marketCache;
    
    @Override
    public void updateTick(TickDto tick) {
        marketCache.updateLatestPrice(tick.getSymbol(), tick.getLtp());
    }
    
    @Override
    public double getLatestPrice(String symbol) {
        Double price = marketCache.getLatestPrice(symbol);
        return price != null ? price : 0.0;
    }

    @Override
    public List<String> getRecentlyUpdatedSymbols(Duration maxAge) {
        return marketCache.getRecentlyUpdatedSymbols(maxAge);
    }
    
    @Override
    public void clearCache() {
        marketCache.clearCache();
    }
}
