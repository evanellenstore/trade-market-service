package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects the Descending Triangle chart pattern.
 *
 * Pattern Characteristics:
 * - Flat horizontal support (lows touch same level multiple times)
 * - Falling resistance line (highs getting lower)
 * - Converging trendlines forming a triangle
 * - Bearish breakdown below support expected
 * - Volume decreases during consolidation
 * - Volume increases on breakdown
 *
 * Signal: Bearish continuation - expect downtrend to resume
 * Target: Support - (support - resistance)
 * Stop Loss: Above the falling resistance line
 */
@Component
@Slf4j
public class DescendingTriangleDetector implements PatternDetector {

    private final PivotDetector pivotDetector;
    private final PatternUtils patternUtils;

    // Configuration constants
    private static final int MIN_PATTERN_CANDLES = 15;
    private static final double SUPPORT_TOLERANCE = 0.5;
    private static final double CONVERGENCE_THRESHOLD = 0.95;
    private static final int MIN_TOUCHES = 3;
    private static final double VOLUME_THRESHOLD = 0.8;
    private static final double BREAKOUT_THRESHOLD = 0.5;

    public DescendingTriangleDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }

    @Override
    public ChartPattern pattern() {
        return ChartPattern.DESCENDING_TRIANGLE;
    }

    @Override
    public String description() {
        return "Flat support with falling resistance, converging into bearish breakdown";
    }

    @Override
    public int getPriority() {
        return 55;
    }

    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        if (candles == null || candles.size() < MIN_PATTERN_CANDLES) {
            log.debug("Not enough candles for descending triangle detection: need {}, got {}",
                MIN_PATTERN_CANDLES, candles == null ? 0 : candles.size());
            return null;
        }

        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, 2, 2);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, 2, 2);

        if (swingHighs == null || swingHighs.isEmpty() || swingLows == null || swingLows.size() < MIN_TOUCHES) {
            log.debug("Insufficient pivots for descending triangle: {} highs, {} lows",
                swingHighs == null ? 0 : swingHighs.size(), swingLows == null ? 0 : swingLows.size());
            return null;
        }

        double support = findFlatSupport(swingLows);
        if (support <= 0) {
            log.debug("No flat support level found with {} touches", MIN_TOUCHES);
            return null;
        }

        PivotPoint highestPivot = patternUtils.getHighestPivot(swingHighs);
        if (highestPivot == null) {
            return null;
        }
        double resistance = highestPivot.getPrice();

        if (resistance <= support) {
            log.debug("Resistance {} not above support {}", resistance, support);
            return null;
        }

        if (!isConverging(swingHighs, swingLows)) {
            log.debug("Swing highs/lows are not converging - not a valid triangle");
            return null;
        }

        double patternHeight = resistance - support;
        double target = support - patternHeight;
        double stopLoss = resistance + (patternHeight * 0.1);

        Candle lastCandle = candles.get(candles.size() - 1);
        int confidence = calculateConfidence(candles, swingHighs, swingLows, support, resistance, lastCandle);

        return PatternResult.builder()
            .pattern(ChartPattern.DESCENDING_TRIANGLE)
            .confidence(confidence)
            .breakoutPrice(lastCandle.getClose())
            .stopLoss(stopLoss)
            .target(target)
            .secondaryTarget(support)
            .description("Descending Triangle: Support=" + String.format("%.2f", support) +
                ", Resistance=" + String.format("%.2f", resistance))
            .patternLength(swingHighs.get(swingHighs.size() - 1).getIndex() - swingLows.get(0).getIndex())
            .direction("BEARISH")
            .riskRewardRatio(patternUtils.calculateRiskRewardRatio())
            .detectionTime(System.currentTimeMillis())
            .build();
    }

    private double findFlatSupport(List<PivotPoint> swingLows) {
        if (swingLows.size() < MIN_TOUCHES) {
            return 0;
        }

        double firstLevel = swingLows.get(swingLows.size() - MIN_TOUCHES).getPrice();
        int count = 0;

        for (PivotPoint low : swingLows) {
            if (patternUtils.pricesApproximatelyEqual(low.getPrice(), firstLevel, SUPPORT_TOLERANCE)) {
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

        if (initialSpread <= 0) {
            return false;
        }

        return finalSpread < initialSpread * CONVERGENCE_THRESHOLD;
    }

    private int calculateConfidence(List<Candle> candles, List<PivotPoint> swingHighs,
                                     List<PivotPoint> swingLows, double support, double resistance,
                                     Candle lastCandle) {
        int confidence = 40;

        // Support touches
        int supportTouches = 0;
        for (PivotPoint low : swingLows) {
            if (patternUtils.pricesApproximatelyEqual(low.getPrice(), support, SUPPORT_TOLERANCE)) {
                supportTouches++;
            }
        }
        if (supportTouches >= 4) confidence += 15;
        else if (supportTouches >= 3) confidence += 10;

        // Falling resistance (lower highs)
        if (patternUtils.isLowerHighs(candles.subList(Math.max(0, candles.size() - 15), candles.size()))) {
            confidence += 15;
        }

        // Volume should contract during consolidation
        double patternAvgVolume = patternUtils.calculateAverageVolume(
            candles.subList(Math.max(0, candles.size() - MIN_PATTERN_CANDLES), candles.size() - 1), MIN_PATTERN_CANDLES - 1);
        double priorAvgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (priorAvgVolume > 0 && patternAvgVolume <= priorAvgVolume * VOLUME_THRESHOLD) {
            confidence += 10;
        }

        // Breakdown volume expansion
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (avgVolume > 0 && lastCandle.getVolume() > avgVolume * 1.2) {
            confidence += 15;
        }

        // Actual bearish breakdown below support (direction matters, not just magnitude)
        double breakdown = Math.abs(patternUtils.percentageDifference(support, lastCandle.getClose()));
        if (lastCandle.getClose() < support && breakdown > BREAKOUT_THRESHOLD) {
            confidence += 15;
        }

        return Math.min(100, confidence);
    }
}