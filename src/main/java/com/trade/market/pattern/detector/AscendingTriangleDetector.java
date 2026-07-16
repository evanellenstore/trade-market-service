package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects the Ascending Triangle chart pattern.
 * 
 * Pattern Characteristics:
 * - Flat horizontal resistance (highs touch same level multiple times)
 * - Rising support line (lows getting higher)
 * - Converging trendlines forming a triangle
 * - Bullish breakout above resistance expected
 * - Volume decreases during consolidation
 * - Volume increases on breakout
 * 
 * Signal: Bullish continuation - expect uptrend to resume
 * Target: Resistance + (resistance - support)
 * Stop Loss: Below the rising support line
 */
@Component
@Slf4j
public class AscendingTriangleDetector implements PatternDetector {
    
    private final PivotDetector pivotDetector;
    private final PatternUtils patternUtils;
    
    // Configuration constants
    private static final int MIN_PATTERN_CANDLES = 15;
    private static final double RESISTANCE_TOLERANCE = 0.5; // 0.5% tolerance for resistance
    private static final double CONVERGENCE_THRESHOLD = 0.95; // 95% converged
    private static final int MIN_TOUCHES = 3;
    private static final double VOLUME_THRESHOLD = 0.8;
    private static final double BREAKOUT_THRESHOLD = 0.5;
    
    public AscendingTriangleDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }
    
    @Override
    public ChartPattern pattern() {
        return ChartPattern.ASCENDING_TRIANGLE;
    }
    
    @Override
    public String description() {
        return "Flat resistance with rising support, converging into bullish breakout";
    }
    
    @Override
    public int getPriority() {
        return 55; // Medium priority
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
        
        // Find flat resistance level (highs at same level)
        double resistance = findFlatResistance(swingHighs);
        if (resistance <= 0) {
            return false;
        }
        
        // Check for rising support
        if (!patternUtils.isHigherLows(candles.subList(Math.max(0, candles.size() - 15), candles.size()))) {
            return false;
        }
        
        // Check for convergence
        if (!isConverging(swingHighs, swingLows)) {
            return false;
        }
        
        // Check breakout
        Candle lastCandle = candles.get(candles.size() - 1);
        if (lastCandle.getClose() <= resistance) {
            return false;
        }
        
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() < avgVolume * VOLUME_THRESHOLD) {
            return false;
        }
        
        log.info("Ascending Triangle detected: Resistance={}, Support={}", 
            String.format("%.2f", resistance),
            String.format("%.2f", swingLows.get(swingLows.size() - 1).getPrice()));
        
        return true;
    }
    
    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        if (!detect(candles)) {
            return PatternResult.none();
        }
        
        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, 2, 2);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, 2, 2);
        
        double resistance = findFlatResistance(swingHighs);
        double support = patternUtils.getLowestPivot(swingLows).getPrice();
        
        double patternHeight = resistance - support;
        double target = resistance + patternHeight;
        double stopLoss = support - (patternHeight * 0.1);
        
        Candle lastCandle = candles.get(candles.size() - 1);
        int confidence = calculateConfidence(candles, swingHighs, swingLows, resistance, lastCandle);
        
        return PatternResult.builder()
            .pattern(ChartPattern.ASCENDING_TRIANGLE)
            .confidence(confidence)
            .breakoutPrice(lastCandle.getClose())
            .stopLoss(stopLoss)
            .target(target)
            .secondaryTarget(resistance)
            .description("Ascending Triangle: Resistance=" + String.format("%.2f", resistance) +
                ", Support=" + String.format("%.2f", support))
            .patternLength(swingHighs.get(swingHighs.size() - 1).getIndex() - swingLows.get(0).getIndex())
            .direction("BULLISH")
            .riskRewardRatio(patternUtils.calculateRiskRewardRatio())
            .detectionTime(System.currentTimeMillis())
            .build();
    }
    
    private double findFlatResistance(List<PivotPoint> swingHighs) {
        if (swingHighs.size() < MIN_TOUCHES) {
            return 0;
        }
        
        // Find the mode (most common resistance level)
        double firstLevel = swingHighs.get(swingHighs.size() - MIN_TOUCHES).getPrice();
        int count = 0;
        
        for (PivotPoint high : swingHighs) {
            if (patternUtils.pricesApproximatelyEqual(high.getPrice(), firstLevel, RESISTANCE_TOLERANCE)) {
                count++;
            }
        }
        
        return count >= MIN_TOUCHES ? firstLevel : 0;
    }
    
    private boolean isConverging(List<PivotPoint> swingHighs, List<PivotPoint> swingLows) {
        if (swingHighs.size() < 2 || swingLows.size() < 2) {
            return false;
        }
        
        PivotPoint highStart = swingHighs.get(0);
        PivotPoint highEnd = swingHighs.get(swingHighs.size() - 1);
        PivotPoint lowStart = swingLows.get(0);
        PivotPoint lowEnd = swingLows.get(swingLows.size() - 1);
        
        double initialSpread = highStart.getPrice() - lowStart.getPrice();
        double finalSpread = highEnd.getPrice() - lowEnd.getPrice();
        
        return finalSpread < initialSpread * CONVERGENCE_THRESHOLD;
    }
    
    private int calculateConfidence(List<Candle> candles, List<PivotPoint> swingHighs, 
                                    List<PivotPoint> swingLows, double resistance, Candle lastCandle) {
        int confidence = 50;
        
        // Pattern touches
        int resistanceTouches = 0;
        for (PivotPoint high : swingHighs) {
            if (patternUtils.pricesApproximatelyEqual(high.getPrice(), resistance, RESISTANCE_TOLERANCE)) {
                resistanceTouches++;
            }
        }
        if (resistanceTouches >= 4) confidence += 15;
        else if (resistanceTouches >= 3) confidence += 10;
        
        // Rising support
        if (patternUtils.isHigherLows(candles.subList(Math.max(0, candles.size() - 15), candles.size()))) {
            confidence += 20;
        }
        
        // Volume
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() > avgVolume * 1.2) {
            confidence += 20;
        }
        
        // Breakout percentage
        double breakout = Math.abs(patternUtils.percentageDifference(resistance, lastCandle.getClose()));
        if (breakout > 1.0) confidence += 15;
        
        return Math.min(100, confidence);
    }
}
