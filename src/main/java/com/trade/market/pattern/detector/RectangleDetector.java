package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects the Rectangle chart pattern.
 * 
 * Pattern Characteristics:
 * - Horizontal support level (multiple touches at bottom)
 * - Horizontal resistance level (multiple touches at top)
 * - Price consolidates between two levels
 * - Volume decreases during consolidation
 * - Breakout can be in either direction
 * - Minimum 3 touches on each level
 * 
 * Signal: Neutral until breakout - continuation or reversal
 * Target: Depends on breakout direction (rectangle height projected)
 * Stop Loss: Opposite side of breakout
 */
@Component
@Slf4j
public class RectangleDetector implements PatternDetector {
    
    private final PivotDetector pivotDetector;
    private final PatternUtils patternUtils;
    
    // Configuration constants
    private static final int MIN_PATTERN_CANDLES = 15;
    private static final double RESISTANCE_TOLERANCE = 0.5;
    private static final double SUPPORT_TOLERANCE = 0.5;
    private static final int MIN_TOUCHES = 3;
    private static final double VOLUME_THRESHOLD = 0.8;
    private static final double BREAKOUT_THRESHOLD = 0.5;
    
    public RectangleDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }
    
    @Override
    public ChartPattern pattern() {
        return ChartPattern.RECTANGLE;
    }
    
    @Override
    public String description() {
        return "Horizontal consolidation between support and resistance levels";
    }
    
    @Override
    public int getPriority() {
        return 45;
    }
    
    @Override
    public boolean detect(List<Candle> candles) {
        if (candles.size() < MIN_PATTERN_CANDLES) {
            return false;
        }
        
        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, 2, 2);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, 2, 2);
        
        if (swingHighs.size() < MIN_TOUCHES || swingLows.size() < MIN_TOUCHES) {
            return false;
        }
        
        // Find flat resistance
        double resistance = findFlatLevel(swingHighs, RESISTANCE_TOLERANCE);
        if (resistance <= 0) {
            return false;
        }
        
        // Find flat support
        double support = findFlatLevel(swingLows, SUPPORT_TOLERANCE);
        if (support <= 0) {
            return false;
        }
        
        // Ensure support < resistance
        if (support >= resistance) {
            return false;
        }
        
        // Check rectangle height (should be reasonable, not too small)
        double height = resistance - support;
        double heightPercent = patternUtils.percentageDifference(support, resistance);
        if (heightPercent < 1.0) {
            return false;
        }
        
        // Check for breakout
        Candle lastCandle = candles.get(candles.size() - 1);
        boolean breakoutAbove = lastCandle.getClose() > resistance;
        boolean breakoutBelow = lastCandle.getClose() < support;
        
        if (!breakoutAbove && !breakoutBelow) {
            return false;
        }
        
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() < avgVolume * VOLUME_THRESHOLD) {
            return false;
        }
        
        log.info("Rectangle detected: Support={}, Resistance={}, Height={}", 
            String.format("%.2f", support),
            String.format("%.2f", resistance),
            String.format("%.2f%%", heightPercent));
        
        return true;
    }
    
    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        if (!detect(candles)) {
            return PatternResult.none();
        }
        
        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, 2, 2);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, 2, 2);
        
        double resistance = findFlatLevel(swingHighs, RESISTANCE_TOLERANCE);
        double support = findFlatLevel(swingLows, SUPPORT_TOLERANCE);
        Candle lastCandle = candles.get(candles.size() - 1);
        
        double height = resistance - support;
        double target, stopLoss;
        String direction;
        
        if (lastCandle.getClose() > resistance) {
            // Breakout above
            target = resistance + height;
            stopLoss = support - (height * 0.1);
            direction = "BULLISH";
        } else {
            // Breakdown below
            target = support - height;
            stopLoss = resistance + (height * 0.1);
            direction = "BEARISH";
        }
        
        int confidence = calculateConfidence(candles, swingHighs, swingLows, resistance, support, lastCandle);
        
        return PatternResult.builder()
            .pattern(ChartPattern.RECTANGLE)
            .confidence(confidence)
            .breakoutPrice(lastCandle.getClose())
            .stopLoss(stopLoss)
            .target(target)
            .secondaryTarget((resistance + support) / 2.0)
            .description("Rectangle: Support=" + String.format("%.2f", support) +
                ", Resistance=" + String.format("%.2f", resistance) +
                ", Breakout=" + direction)
            .patternLength(candles.size() - Math.max(swingLows.get(0).getIndex(), swingHighs.get(0).getIndex()))
            .direction(direction)
            .riskRewardRatio(patternUtils.calculateRiskRewardRatio())
            .detectionTime(System.currentTimeMillis())
            .build();
    }
    
    private double findFlatLevel(List<PivotPoint> pivots, double tolerance) {
        if (pivots.size() < MIN_TOUCHES) {
            return 0;
        }
        
        // Start from most recent and find cluster
        double baseLevel = pivots.get(pivots.size() - 1).getPrice();
        int count = 0;
        
        for (int i = pivots.size() - 1; i >= 0; i--) {
            if (patternUtils.pricesApproximatelyEqual(pivots.get(i).getPrice(), baseLevel, tolerance)) {
                count++;
            }
        }
        
        return count >= MIN_TOUCHES ? baseLevel : 0;
    }
    
    private int calculateConfidence(List<Candle> candles, List<PivotPoint> swingHighs,
                                    List<PivotPoint> swingLows, double resistance, double support,
                                    Candle lastCandle) {
        int confidence = 50;
        
        // Multiple touches
        if (swingHighs.size() >= 5) confidence += 10;
        if (swingLows.size() >= 5) confidence += 10;
        
        // Resistance touches
        int resistanceTouches = 0;
        for (PivotPoint high : swingHighs) {
            if (patternUtils.pricesApproximatelyEqual(high.getPrice(), resistance, RESISTANCE_TOLERANCE)) {
                resistanceTouches++;
            }
        }
        if (resistanceTouches >= 4) confidence += 15;
        else if (resistanceTouches >= 3) confidence += 10;
        
        // Support touches
        int supportTouches = 0;
        for (PivotPoint low : swingLows) {
            if (patternUtils.pricesApproximatelyEqual(low.getPrice(), support, SUPPORT_TOLERANCE)) {
                supportTouches++;
            }
        }
        if (supportTouches >= 4) confidence += 15;
        else if (supportTouches >= 3) confidence += 10;
        
        // Volume
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() > avgVolume * 1.2) {
            confidence += 15;
        }
        
        return Math.min(100, confidence);
    }
}
