package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects Resistance Breakout pattern.
 * 
 * Pattern Characteristics:
 * - Resistance level touched 3+ times without breaking through
 * - Price finally breaks above resistance
 * - Close above resistance level with volume confirmation
 * - Bullish candle formation on breakout
 * - High volume supporting the breakout
 * 
 * Signal: Bullish breakout - expect uptrend continuation
 * Target: Resistance + (resistance - previous support)
 * Stop Loss: Resistance level (tight stop)
 */
@Component
@Slf4j
public class ResistanceBreakoutDetector implements PatternDetector {
    
    private final PivotDetector pivotDetector;
    private final PatternUtils patternUtils;
    
    // Configuration constants
    private static final int MIN_LOOKBACK = 20;
    private static final double RESISTANCE_TOLERANCE = 0.5;
    private static final int MIN_TOUCHES = 3;
    private static final double VOLUME_THRESHOLD = 0.8;
    private static final double BREAKOUT_THRESHOLD = 0.5;
    
    public ResistanceBreakoutDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }
    
    @Override
    public ChartPattern pattern() {
        return ChartPattern.RESISTANCE_BREAKOUT;
    }
    
    @Override
    public String description() {
        return "Price breaks above resistance level after multiple touches with volume confirmation";
    }
    
    @Override
    public int getPriority() {
        return 50;
    }
    
    @Override
    public boolean detect(List<Candle> candles) {
        if (candles.size() < MIN_LOOKBACK) {
            return false;
        }
        
        Candle lastCandle = candles.get(candles.size() - 1);
        
        // Find recent resistance level
        List<PivotPoint> recentHighs = pivotDetector.detectSwingHighs(
            candles.subList(Math.max(0, candles.size() - MIN_LOOKBACK), candles.size()), 2, 2
        );
        
        if (recentHighs.isEmpty()) {
            return false;
        }
        
        // Find most significant resistance (highest that was touched multiple times)
        double resistance = findResistanceLevel(candles, recentHighs);
        if (resistance <= 0 || lastCandle.getClose() <= resistance) {
            return false;
        }
        
        // Verify breakout above resistance
        double breakoutPercent = Math.abs(patternUtils.percentageDifference(resistance, lastCandle.getClose()));
        if (breakoutPercent < BREAKOUT_THRESHOLD) {
            return false;
        }
        
        // Volume confirmation
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() < avgVolume * VOLUME_THRESHOLD) {
            return false;
        }
        
        // Bullish candle confirmation
        if (lastCandle.getClose() <= lastCandle.getOpen()) {
            return false;
        }
        
        log.info("Resistance Breakout detected: Resistance={}, Breakout={}", 
            String.format("%.2f", resistance),
            String.format("%.2f%%", breakoutPercent));
        
        return true;
    }
    
    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        if (!detect(candles)) {
            return PatternResult.none();
        }
        
        List<PivotPoint> recentHighs = pivotDetector.detectSwingHighs(
            candles.subList(Math.max(0, candles.size() - MIN_LOOKBACK), candles.size()), 2, 2
        );
        
        double resistance = findResistanceLevel(candles, recentHighs);
        
        // Find support (lowest in last MIN_LOOKBACK)
        List<Candle> lookbackCandles = candles.subList(Math.max(0, candles.size() - MIN_LOOKBACK), candles.size());
        double support = lookbackCandles.stream().mapToDouble(Candle::getLow).min().orElse(0);
        
        Candle lastCandle = candles.get(candles.size() - 1);
        
        double projectedDistance = resistance - support;
        double target = resistance + projectedDistance;
        double stopLoss = resistance - (projectedDistance * 0.1);
        
        int confidence = calculateConfidence(candles, resistance, support, lastCandle);
        
        return PatternResult.builder()
            .pattern(ChartPattern.RESISTANCE_BREAKOUT)
            .confidence(confidence)
            .breakoutPrice(lastCandle.getClose())
            .stopLoss(stopLoss)
            .target(target)
            .secondaryTarget(resistance)
            .description("Resistance Breakout: Resistance=" + String.format("%.2f", resistance) +
                ", Support=" + String.format("%.2f", support))
            .patternLength(MIN_LOOKBACK)
            .direction("BULLISH")
            .riskRewardRatio(patternUtils.calculateRiskRewardRatio())
            .detectionTime(System.currentTimeMillis())
            .build();
    }
    
    private double findResistanceLevel(List<Candle> candles, List<PivotPoint> pivots) {
        if (pivots.isEmpty()) {
            return 0;
        }
        
        // Find the highest level that was tested multiple times
        double topLevel = pivots.stream().mapToDouble(PivotPoint::getPrice).max().orElse(0);
        int touches = 0;
        
        for (PivotPoint pivot : pivots) {
            if (patternUtils.pricesApproximatelyEqual(pivot.getPrice(), topLevel, RESISTANCE_TOLERANCE)) {
                touches++;
            }
        }
        
        return touches >= MIN_TOUCHES ? topLevel : 0;
    }
    
    private int calculateConfidence(List<Candle> candles, double resistance, double support, Candle lastCandle) {
        int confidence = 50;
        
        // Breakout strength
        double breakout = Math.abs(patternUtils.percentageDifference(resistance, lastCandle.getClose()));
        if (breakout > 1.5) confidence += 20;
        else if (breakout > 0.8) confidence += 15;
        
        // Volume
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() > avgVolume * 1.3) {
            confidence += 20;
        } else if (lastCandle.getVolume() > avgVolume) {
            confidence += 15;
        }
        
        // Bullish candle
        if (lastCandle.getBody() > lastCandle.getRange() * 0.7) {
            confidence += 15;
        }
        
        return Math.min(100, confidence);
    }
}
