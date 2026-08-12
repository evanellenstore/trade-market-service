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
    public PatternResult detectWithResult(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return PatternResult.builder()
                .pattern(ChartPattern.ASCENDING_TRIANGLE)
                .confidence(0)
                .description("Ascending Triangle: insufficient candle data")
                .direction("BULLISH")
                .detectionTime(System.currentTimeMillis())
                .build();
        }

        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, 2, 2);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, 2, 2);

        // Not enough structure to even attempt the pattern - bail out with zero confidence
        // instead of feeding empty/short lists into resistance/support/height math below.
        if (swingHighs.size() < MIN_TOUCHES || swingLows.isEmpty()) {
            return PatternResult.builder()
                .pattern(ChartPattern.ASCENDING_TRIANGLE)
                .confidence(0)
                .description("Ascending Triangle: not enough pivots to evaluate")
                .direction("BULLISH")
                .detectionTime(System.currentTimeMillis())
                .build();
        }

        double resistance = findFlatResistance(swingHighs);
        double support = patternUtils.getLowestPivot(swingLows).getPrice();

        // No valid flat resistance found, or resistance isn't actually above support -
        // this isn't a valid ascending triangle, don't compute a fake target/stop.
        if (resistance <= 0 || resistance <= support) {
            return PatternResult.builder()
                .pattern(ChartPattern.ASCENDING_TRIANGLE)
                .confidence(0)
                .description("Ascending Triangle: no valid flat resistance above support")
                .direction("BULLISH")
                .detectionTime(System.currentTimeMillis())
                .build();
        }

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
    
    /**
     * Finds the resistance level touched by the most swing highs (mode),
     * rather than anchoring on a single fixed pivot.
     */
    private double findFlatResistance(List<PivotPoint> swingHighs) {
        if (swingHighs.size() < MIN_TOUCHES) {
            return 0;
        }

        double bestLevel = 0;
        int bestCount = 0;

        for (PivotPoint candidate : swingHighs) {
            int count = 0;
            for (PivotPoint high : swingHighs) {
                if (patternUtils.pricesApproximatelyEqual(high.getPrice(), candidate.getPrice(), RESISTANCE_TOLERANCE)) {
                    count++;
                }
            }
            if (count > bestCount) {
                bestCount = count;
                bestLevel = candidate.getPrice();
            }
        }

        return bestCount >= MIN_TOUCHES ? bestLevel : 0;
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

        // Converging trendlines - was previously computed but never used
        if (isConverging(swingHighs, swingLows)) {
            confidence += 10;
        } else {
            // A non-converging channel isn't really a triangle; penalize it
            confidence -= 15;
        }
        
        // Volume - now actually uses the declared threshold constant
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() > avgVolume * (1 + VOLUME_THRESHOLD)) {
            confidence += 20;
        }
        
        // Breakout percentage - now uses the declared threshold constant
        double breakout = Math.abs(patternUtils.percentageDifference(resistance, lastCandle.getClose()));
        if (breakout > BREAKOUT_THRESHOLD) confidence += 15;
        
        return Math.max(0, Math.min(100, confidence));
    }
}