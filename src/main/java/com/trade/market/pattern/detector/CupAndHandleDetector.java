package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects the Cup and Handle chart pattern.
 * 
 * Pattern Characteristics:
 * - Cup: rounded bottom formation (U-shaped)
 * - Handle: small pullback after cup (consolidation)
 * - Left rim and right rim at approximately equal heights
 * - Breakout above the rim level on increasing volume
 * - Cup depth 10-30% of previous uptrend
 * - Handle depth typically 5-10% of cup depth
 * 
 * Signal: Bullish continuation - strong bullish bias
 * Target: Rim + cup depth
 * Stop Loss: Below handle low
 */
@Component
@Slf4j
public class CupAndHandleDetector implements PatternDetector {
    
    private final PivotDetector pivotDetector;
    private final PatternUtils patternUtils;
    
    // Configuration constants
    private static final int MIN_CUP_CANDLES = 15;
    private static final int MIN_HANDLE_CANDLES = 3;
    private static final double CUP_DEPTH_MIN = 10.0;
    private static final double CUP_DEPTH_MAX = 30.0;
    private static final double RIM_TOLERANCE = 1.0;
    private static final double HANDLE_TOLERANCE = 2.0;
    private static final double VOLUME_THRESHOLD = 0.8;
    
    public CupAndHandleDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }
    
    @Override
    public ChartPattern pattern() {
        return ChartPattern.CUP_AND_HANDLE;
    }
    
    @Override
    public String description() {
        return "Rounded bottom (cup) with shallow pullback (handle), bullish continuation";
    }
    
    @Override
    public int getPriority() {
        return 60;
    }
    
    @Override
    public boolean detect(List<Candle> candles) {
        if (candles.size() < MIN_CUP_CANDLES + MIN_HANDLE_CANDLES) {
            return false;
        }
        
        // Identify cup and handle regions
        int handleStart = Math.max(MIN_CUP_CANDLES, candles.size() - MIN_HANDLE_CANDLES - 5);
        List<Candle> cupCandles = candles.subList(0, handleStart);
        List<Candle> handleCandles = candles.subList(handleStart, candles.size());
        
        // Find cup high (rim level)
        double cupHigh = cupCandles.stream().mapToDouble(Candle::getHigh).max().orElse(0);
        double cupLow = cupCandles.stream().mapToDouble(Candle::getLow).min().orElse(Double.MAX_VALUE);
        double cupDepth = patternUtils.percentageDifference(cupHigh, cupLow);
        
        // Check cup depth
        if (cupDepth < CUP_DEPTH_MIN || cupDepth > CUP_DEPTH_MAX) {
            return false;
        }
        
        // Check for rounded bottom (not V-shaped)
        if (!isRoundedBottom(cupCandles, cupLow)) {
            return false;
        }
        
        // Find handle high and low
        double handleHigh = handleCandles.stream().mapToDouble(Candle::getHigh).max().orElse(0);
        double handleLow = handleCandles.stream().mapToDouble(Candle::getLow).min().orElse(Double.MAX_VALUE);
        double handleDepth = patternUtils.percentageDifference(handleHigh, handleLow);
        
        // Handle should be 5-15% of cup depth
        if (handleDepth > cupDepth * 0.15) {
            return false;
        }
        
        // Check for breakout
        Candle lastCandle = candles.get(candles.size() - 1);
        if (lastCandle.getClose() <= cupHigh) {
            return false;
        }
        
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() < avgVolume * VOLUME_THRESHOLD) {
            return false;
        }
        
        log.info("Cup and Handle detected: Cup Depth={}, Handle Depth={}", 
            String.format("%.2f%%", cupDepth),
            String.format("%.2f%%", handleDepth));
        
        return true;
    }
    
    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        if (!detect(candles)) {
            return PatternResult.none();
        }
        
        int handleStart = Math.max(MIN_CUP_CANDLES, candles.size() - MIN_HANDLE_CANDLES - 5);
        List<Candle> cupCandles = candles.subList(0, handleStart);
        
        double cupHigh = cupCandles.stream().mapToDouble(Candle::getHigh).max().orElse(0);
        double cupLow = cupCandles.stream().mapToDouble(Candle::getLow).min().orElse(Double.MAX_VALUE);
        double cupDepth = cupHigh - cupLow;
        
        Candle lastCandle = candles.get(candles.size() - 1);
        
        double target = cupHigh + cupDepth;
        double stopLoss = cupLow - (cupDepth * 0.1);
        
        int confidence = calculateConfidence(candles, cupHigh, cupLow, lastCandle);
        
        return PatternResult.builder()
            .pattern(ChartPattern.CUP_AND_HANDLE)
            .confidence(confidence)
            .breakoutPrice(lastCandle.getClose())
            .stopLoss(stopLoss)
            .target(target)
            .secondaryTarget(cupHigh)
            .description("Cup and Handle: Rim=" + String.format("%.2f", cupHigh) +
                ", Bottom=" + String.format("%.2f", cupLow) +
                ", Depth=" + String.format("%.2f%%", cupDepth/cupHigh*100))
            .patternLength(candles.size())
            .direction("BULLISH")
            .riskRewardRatio(patternUtils.calculateRiskRewardRatio())
            .detectionTime(System.currentTimeMillis())
            .build();
    }
    
    private boolean isRoundedBottom(List<Candle> candles, double bottomPrice) {
        // Check if bottom has gradual approach (not sharp V)
        int bottomIndex = -1;
        for (int i = 0; i < candles.size(); i++) {
            if (candles.get(i).getLow() == bottomPrice) {
                bottomIndex = i;
                break;
            }
        }
        
        if (bottomIndex < 0 || bottomIndex > candles.size() - 2) {
            return false;
        }
        
        // Check for rounding (multiple candles near bottom)
        int nearBottomCount = 0;
        double tolerance = bottomPrice * 0.01; // 1% tolerance
        
        for (Candle candle : candles) {
            if (candle.getLow() <= bottomPrice + tolerance) {
                nearBottomCount++;
            }
        }
        
        return nearBottomCount >= 3; // At least 3 candles near the bottom
    }
    
    private int calculateConfidence(List<Candle> candles, double cupHigh, double cupLow, Candle lastCandle) {
        int confidence = 55;
        
        // Cup formation quality
        double cupDepth = cupHigh - cupLow;
        if (cupDepth > 15) confidence += 15;
        else if (cupDepth > 10) confidence += 10;
        
        // Volume
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() > avgVolume * 1.2) {
            confidence += 20;
        }
        
        // Breakout
        double breakout = Math.abs(patternUtils.percentageDifference(cupHigh, lastCandle.getClose()));
        if (breakout > 1.0) confidence += 15;
        
        return Math.min(100, confidence);
    }
}
