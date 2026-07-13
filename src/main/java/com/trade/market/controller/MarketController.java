package com.trade.market.controller;

import com.trade.market.service.CandleService;
import com.trade.market.util.ApiResponse;
import com.trade.market.cache.MarketCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/market")
@Slf4j
@RequiredArgsConstructor
public class MarketController {
    
    private final CandleService candleService;
    private final MarketCache marketCache;
    
    @GetMapping("/latest")
    public ResponseEntity<ApiResponse<Object>> getLatestPrice(@RequestParam String symbol) {
        log.info("Fetching latest price for symbol: {}", symbol);
        Double price = marketCache.getLatestPrice(symbol);
        return ResponseEntity.ok(ApiResponse.success(price, "Latest price retrieved"));
    }
    
    @GetMapping("/candles")
    public ResponseEntity<ApiResponse<Object>> getCandles(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "ONE_MINUTE") String timeframe,
            @RequestParam(defaultValue = "100") int limit) {
        log.info("Fetching candles for symbol: {}, timeframe: {}", symbol, timeframe);
        var candles = candleService.getCandles(symbol, timeframe, limit);
        return ResponseEntity.ok(ApiResponse.success(candles, "Candles retrieved"));
    }
    
    @GetMapping("/indicator")
    public ResponseEntity<ApiResponse<Object>> getIndicators(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "ONE_MINUTE") String timeframe) {
        log.info("Fetching indicators for symbol: {}, timeframe: {}", symbol, timeframe);
        var indicators = candleService.getIndicators(symbol, timeframe);
        return ResponseEntity.ok(ApiResponse.success(indicators, "Indicators retrieved"));
    }
    
    @GetMapping("/health")
    public ResponseEntity<ApiResponse<Object>> health() {
        return ResponseEntity.ok(ApiResponse.success("UP", "Service is running"));
    }
}
