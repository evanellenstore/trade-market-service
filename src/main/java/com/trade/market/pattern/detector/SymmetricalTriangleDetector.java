package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects the Symmetrical Triangle chart pattern.
 * 
 * Pattern Characteristics:
 * - Lower highs forming downtrend resistance
 * - Higher lows forming uptrend support
 * - Resistance and support lines converge
 * - Breakout can be in either direction (usually in direction of prior trend)
 * - Volume decreases during consolidation
 * - Volume increases on breakout
 * 
 * Signal: Neutral until breakout - continuation in either direction
 * Target: Depends on breakout direction
 * Stop Loss: Opposite side of breakout
 */
@Component
@Slf4j
public class SymmetricalTriangleDetector implements PatternDetector {
    
    private final PivotDetector pivotDetector;
    private final PatternUtils patternUtils;
    
    // Configuration constants
    private static final int MIN_PATTERN_CANDLES = 15;
    private static final double CONVERGENCE_THRESHOLD = 0.8; // 80% converged
    private static final int MIN_TOUCHES = 3;
    private static final double VOLUME_THRESHOLD = 0.8;
    private static final double BREAKOUT_THRESHOLD = 0.5;
    
    public SymmetricalTriangleDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }
    
    @Override
    public ChartPattern pattern() {
        return ChartPattern.SYMMETRICAL_TRIANGLE;
    }
    
    @Override
    public String description() {
        return "Converging lower highs and higher lows, forming neutral consolidation pattern";
    }
    
    @Override
    public int getPriority() {
        return 50;
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
        
        // Check for lower highs
        if (!patternUtils.isLowerHighs(candles.subList(Math.max(0, candles.size() - 15), candles.size()))) {
            return false;
        }
        
        // Check for higher lows
        if (!patternUtils.isHigherLows(candles.subList(Math.max(0, candles.size() - 15), candles.size()))) {
            return false;
        }
        
        // Check for convergence
        if (!isConverging(swingHighs, swingLows)) {
            return false;
        }
        
        // Check for breakout (in either direction)
        Candle lastCandle = candles.get(candles.size() - 1);
        double resistance = patternUtils.getHighestPivot(swingHighs).getPrice();
        double support = patternUtils.getLowestPivot(swingLows).getPrice();
        
        boolean breakoutAbove = lastCandle.getClose() > resistance && 
            Math.abs(patternUtils.percentageDifference(resistance, lastCandle.getClose())) > BREAKOUT_THRESHOLD;
        boolean breakoutBelow = lastCandle.getClose() < support && 
            Math.abs(patternUtils.percentageDifference(support, lastCandle.getClose())) > BREAKOUT_THRESHOLD;
        
        if (!breakoutAbove && !breakoutBelow) {
            return false;
        }
        
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() < avgVolume * VOLUME_THRESHOLD) {
            return false;
        }
        
        log.info("Symmetrical Triangle detected: Resistance={}, Support={}", 
            String.format("%.2f", resistance),
            String.format("%.2f", support));
        
        return true;
    }
    
    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        if (!detect(candles)) {
            return PatternResult.none();
        }
        
        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, 2, 2);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, 2, 2);
        
        double resistance = patternUtils.getHighestPivot(swingHighs).getPrice();
        double support = patternUtils.getLowestPivot(swingLows).getPrice();
        Candle lastCandle = candles.get(candles.size() - 1);
        
        double patternHeight = resistance - support;
        double target, stopLoss;
        String direction;
        
        if (lastCandle.getClose() > resistance) {
            // Bullish breakout
            target = resistance + patternHeight;
            stopLoss = support - (patternHeight * 0.1);
            direction = "BULLISH";
        } else {
            // Bearish breakdown
            target = support - patternHeight;
            stopLoss = resistance + (patternHeight * 0.1);
            direction = "BEARISH";
        }
        
        int confidence = calculateConfidence(candles, swingHighs, swingLows, resistance, support, lastCandle);
        
        return PatternResult.builder()
            .pattern(ChartPattern.SYMMETRICAL_TRIANGLE)
            .confidence(confidence)
            .breakoutPrice(lastCandle.getClose())
            .stopLoss(stopLoss)
            .target(target)
            .secondaryTarget((resistance + support) / 2.0)
            .description("Symmetrical Triangle: Resistance=" + String.format("%.2f", resistance) +
                ", Support=" + String.format("%.2f", support) +
                ", Breakout=" + direction)
            .patternLength(swingHighs.get(swingHighs.size() - 1).getIndex() - swingLows.get(0).getIndex())
            .direction(direction)
            .riskRewardRatio(patternUtils.calculateRiskRewardRatio())
            .detectionTime(System.currentTimeMillis())
            .build();
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
        
        // Convergence should be at least 20% reduction
        return finalSpread < initialSpread * CONVERGENCE_THRESHOLD;
    }
    
    private int calculateConfidence(List<Candle> candles, List<PivotPoint> swingHighs,
                                    List<PivotPoint> swingLows, double resistance, double support,
                                    Candle lastCandle) {
        int confidence = 50;
        
        // Pattern touches
        if (swingHighs.size() >= 4) confidence += 10;
        if (swingLows.size() >= 4) confidence += 10;
        
        // Lower highs
        if (patternUtils.isLowerHighs(candles.subList(Math.max(0, candles.size() - 15), candles.size()))) {
            confidence += 15;
        }
        
        // Higher lows
        if (patternUtils.isHigherLows(candles.subList(Math.max(0, candles.size() - 15), candles.size()))) {
            confidence += 15;
        }
        
        // Volume
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() > avgVolume * 1.2) {
            confidence += 20;
        }
        
        // Breakout confirmation
        boolean breakoutAbove = lastCandle.getClose() > resistance;
        boolean breakoutBelow = lastCandle.getClose() < support;
        if (breakoutAbove || breakoutBelow) {
            double breakoutPercent = breakoutAbove ?
                Math.abs(patternUtils.percentageDifference(resistance, lastCandle.getClose())) :
                Math.abs(patternUtils.percentageDifference(support, lastCandle.getClose()));
            if (breakoutPercent > 1.0) confidence += 15;
        }
        
        return Math.min(100, confidence);
    }
}
