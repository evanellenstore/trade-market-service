package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects Support Breakdown pattern.
 * 
 * Pattern Characteristics:
 * - Support level touched 3+ times without breaking through
 * - Price finally breaks below support
 * - Close below support level with volume confirmation
 * - Bearish candle formation on breakdown
 * - High volume supporting the breakdown
 * 
 * Signal: Bearish breakdown - expect downtrend continuation
 * Target: Support - (previous resistance - support)
 * Stop Loss: Support level (tight stop)
 */
@Component
@Slf4j
public class SupportBreakdownDetector implements PatternDetector {
    
    private final PivotDetector pivotDetector;
    private final PatternUtils patternUtils;
    
    // Configuration constants
    private static final int MIN_LOOKBACK = 20;
    private static final double SUPPORT_TOLERANCE = 0.5;
    private static final int MIN_TOUCHES = 3;
    private static final double VOLUME_THRESHOLD = 0.8;
    private static final double BREAKDOWN_THRESHOLD = 0.5;
    
    public SupportBreakdownDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }
    
    @Override
    public ChartPattern pattern() {
        return ChartPattern.SUPPORT_BREAKDOWN;
    }
    
    @Override
    public String description() {
        return "Price breaks below support level after multiple touches with volume confirmation";
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
        
        // Find recent support level
        List<PivotPoint> recentLows = pivotDetector.detectSwingLows(
            candles.subList(Math.max(0, candles.size() - MIN_LOOKBACK), candles.size()), 2, 2
        );
        
        if (recentLows.isEmpty()) {
            return false;
        }
        
        // Find most significant support (lowest that was tested multiple times)
        double support = findSupportLevel(candles, recentLows);
        if (support <= 0 || lastCandle.getClose() >= support) {
            return false;
        }
        
        // Verify breakdown below support
        double breakdownPercent = Math.abs(patternUtils.percentageDifference(support, lastCandle.getClose()));
        if (breakdownPercent < BREAKDOWN_THRESHOLD) {
            return false;
        }
        
        // Volume confirmation
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() < avgVolume * VOLUME_THRESHOLD) {
            return false;
        }
        
        // Bearish candle confirmation
        if (lastCandle.getClose() >= lastCandle.getOpen()) {
            return false;
        }
        
        log.info("Support Breakdown detected: Support={}, Breakdown={}", 
            String.format("%.2f", support),
            String.format("%.2f%%", breakdownPercent));
        
        return true;
    }
    
    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        if (!detect(candles)) {
            return PatternResult.none();
        }
        
        List<PivotPoint> recentLows = pivotDetector.detectSwingLows(
            candles.subList(Math.max(0, candles.size() - MIN_LOOKBACK), candles.size()), 2, 2
        );
        
        double support = findSupportLevel(candles, recentLows);
        
        // Find resistance (highest in last MIN_LOOKBACK)
        List<Candle> lookbackCandles = candles.subList(Math.max(0, candles.size() - MIN_LOOKBACK), candles.size());
        double resistance = lookbackCandles.stream().mapToDouble(Candle::getHigh).max().orElse(Double.MAX_VALUE);
        
        Candle lastCandle = candles.get(candles.size() - 1);
        
        double projectedDistance = resistance - support;
        double target = support - projectedDistance;
        double stopLoss = support + (projectedDistance * 0.1);
        
        int confidence = calculateConfidence(candles, support, resistance, lastCandle);
        
        return PatternResult.builder()
            .pattern(ChartPattern.SUPPORT_BREAKDOWN)
            .confidence(confidence)
            .breakoutPrice(lastCandle.getClose())
            .stopLoss(stopLoss)
            .target(target)
            .secondaryTarget(support)
            .description("Support Breakdown: Support=" + String.format("%.2f", support) +
                ", Resistance=" + String.format("%.2f", resistance))
            .patternLength(MIN_LOOKBACK)
            .direction("BEARISH")
            .riskRewardRatio(patternUtils.calculateRiskRewardRatio())
            .detectionTime(System.currentTimeMillis())
            .build();
    }
    
    private double findSupportLevel(List<Candle> candles, List<PivotPoint> pivots) {
        if (pivots.isEmpty()) {
            return 0;
        }
        
        // Find the lowest level that was tested multiple times
        double bottomLevel = pivots.stream().mapToDouble(PivotPoint::getPrice).min().orElse(Double.MAX_VALUE);
        int touches = 0;
        
        for (PivotPoint pivot : pivots) {
            if (patternUtils.pricesApproximatelyEqual(pivot.getPrice(), bottomLevel, SUPPORT_TOLERANCE)) {
                touches++;
            }
        }
        
        return touches >= MIN_TOUCHES ? bottomLevel : 0;
    }
    
    private int calculateConfidence(List<Candle> candles, double support, double resistance, Candle lastCandle) {
        int confidence = 50;
        
        // Breakdown strength
        double breakdown = Math.abs(patternUtils.percentageDifference(support, lastCandle.getClose()));
        if (breakdown > 1.5) confidence += 20;
        else if (breakdown > 0.8) confidence += 15;
        
        // Volume
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() > avgVolume * 1.3) {
            confidence += 20;
        } else if (lastCandle.getVolume() > avgVolume) {
            confidence += 15;
        }
        
        // Bearish candle
        if (lastCandle.getBody() > lastCandle.getRange() * 0.7) {
            confidence += 15;
        }
        
        return Math.min(100, confidence);
    }
}
