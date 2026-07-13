package com.trade.market.service;

import com.trade.market.dto.TickDto;

/**
 * Service for building 1-minute candles from live ticks
 */
public interface CandleBuilderService {
    
    /**
     * Process incoming tick and update/create candles
     * @param tick the market tick
     */
    void processTick(TickDto tick);
}
