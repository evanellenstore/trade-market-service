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
 * - Price consolidates between two levels for at least MIN_PATTERN_CANDLES
 * - Volume decreases during consolidation
 * - Breakout can be in either direction, and must clear the level by more
 *   than BREAKOUT_THRESHOLD percent to count as a genuine breakout rather
 *   than noise
 * - Minimum 3 touches on each level
 * 
 * Signal: Neutral until breakout - continuation or reversal. No signal is
 * produced while price remains inside the support/resistance range.
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
    private static final double VOLUME_THRESHOLD_MULTIPLIER = 1.2;
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
    public PatternResult detectWithResult(List<Candle> candles) {
        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, 2, 2);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, 2, 2);

        if (swingHighs.isEmpty() || swingLows.isEmpty()) {
            log.debug("RectangleDetector skipped: insufficient pivot points");
            return PatternResult.none();
        }

        double resistance = findFlatLevel(swingHighs, RESISTANCE_TOLERANCE);
        double support = findFlatLevel(swingLows, SUPPORT_TOLERANCE);

        if (resistance == 0 || support == 0) {
            log.debug("RectangleDetector skipped: no flat support/resistance level found");
            return PatternResult.none();
        }

        // Anchor the pattern's start on whichever level's earliest touch came first,
        // so the reported duration reflects the full consolidation, not just the
        // shorter of the two series.
        int patternStartIndex = Math.min(swingLows.get(0).getIndex(), swingHighs.get(0).getIndex());
        int patternLength = candles.size() - patternStartIndex;

        if (patternLength < MIN_PATTERN_CANDLES) {
            log.debug("RectangleDetector skipped: consolidation too short ({} candles)", patternLength);
            return PatternResult.none();
        }

        Candle lastCandle = candles.get(candles.size() - 1);
        double height = resistance - support;

        if (height <= 0) {
            log.debug("RectangleDetector skipped: invalid range (resistance <= support)");
            return PatternResult.none();
        }

        double target;
        double stopLoss;
        String direction;

        double breakoutAbovePercent = patternUtils.percentageDifference(lastCandle.getClose(), resistance);
        double breakdownBelowPercent = patternUtils.percentageDifference(support, lastCandle.getClose());

        if (lastCandle.getClose() > resistance && breakoutAbovePercent >= BREAKOUT_THRESHOLD) {
            // Genuine breakout above resistance
            target = resistance + height;
            stopLoss = support - (height * 0.1);
            direction = "BULLISH";
        } else if (lastCandle.getClose() < support && breakdownBelowPercent >= BREAKOUT_THRESHOLD) {
            // Genuine breakdown below support
            target = support - height;
            stopLoss = resistance + (height * 0.1);
            direction = "BEARISH";
        } else {
            // Price is still inside the range, or has only marginally poked past a
            // level without clearing the noise threshold - no breakout yet.
            log.debug("RectangleDetector skipped: no confirmed breakout (price within range or below threshold)");
            return PatternResult.none();
        }
        
        int confidence = calculateConfidence(candles, swingHighs, swingLows, resistance, support, lastCandle);
        
        return PatternResult.builder()
            .pattern(ChartPattern.RECTANGLE)
            .patternDetected(true)
            .confidence(confidence)
            .breakoutPrice(lastCandle.getClose())
            .stopLoss(stopLoss)
            .target(target)
            .secondaryTarget((resistance + support) / 2.0)
            .description("Rectangle: Support=" + String.format("%.2f", support) +
                ", Resistance=" + String.format("%.2f", resistance) +
                ", Breakout=" + direction)
            .patternLength(patternLength)
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
        if (lastCandle.getVolume() > avgVolume * VOLUME_THRESHOLD_MULTIPLIER) {
            confidence += 15;
        }
        
        return Math.min(100, confidence);
    }
}