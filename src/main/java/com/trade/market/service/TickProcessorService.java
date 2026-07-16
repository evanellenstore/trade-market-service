package com.trade.market.service;

import com.trade.market.dto.TickDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class TickProcessorService {
    
    private final CandleBuilderService candleBuilderService;
    private final MarketCacheService marketCacheService;
    
    /**
     * Validate and process incoming tick
     */
    public void processTick(TickDto tick) {
        
        // Validate tick
        if (tick == null || !isValidTick(tick)) {
            log.warn("Invalid tick received: {}", tick);
            return;
        }
        
        log.debug("Processing valid tick: {} @ {}", tick.getSymbol(), tick.getLtp());
        
        // Update market cache with latest tick
        marketCacheService.updateTick(tick);
        
        // Pass to candle builder
        candleBuilderService.processTick(tick);
    }
    
    private boolean isValidTick(TickDto tick) {
        return tick.getSymbol() != null && !tick.getSymbol().isEmpty();
    }
}
