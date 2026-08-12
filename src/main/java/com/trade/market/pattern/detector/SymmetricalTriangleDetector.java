package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

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
    private static final double VOLUME_THRESHOLD = 0.8; // min volume multiple above average, as a fraction added on top
    private static final double BREAKOUT_THRESHOLD = 0.5; // min % close must clear resistance/support by

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
    public PatternResult detectWithResult(List<Candle> candles) {
        if (candles == null || candles.size() < MIN_PATTERN_CANDLES) {
            return null;
        }

        List<Candle> lookbackCandles = candles.subList(
            Math.max(0, candles.size() - MIN_PATTERN_CANDLES), candles.size());

        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(lookbackCandles, 2, 2);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(lookbackCandles, 2, 2);

        if (swingHighs.size() < MIN_TOUCHES || swingLows.size() < MIN_TOUCHES) {
            return null;
        }

        if (!isConverging(swingHighs, swingLows)) {
            return null;
        }

        double resistance = patternUtils.getHighestPivot(swingHighs).getPrice();
        double support = patternUtils.getLowestPivot(swingLows).getPrice();
        double patternHeight = resistance - support;

        if (patternHeight <= 0) {
            return null;
        }

        Candle lastCandle = candles.get(candles.size() - 1);

        boolean breakoutAbove = lastCandle.getClose() > resistance &&
            patternUtils.percentageDifference(resistance, lastCandle.getClose()) >= BREAKOUT_THRESHOLD;
        boolean breakoutBelow = lastCandle.getClose() < support &&
            patternUtils.percentageDifference(support, lastCandle.getClose()) >= BREAKOUT_THRESHOLD;

        // Still consolidating inside the triangle — no breakout yet, so no signal.
        if (!breakoutAbove && !breakoutBelow) {
            return null;
        }

        double target, stopLoss;
        String direction;

        if (breakoutAbove) {
            target = resistance + patternHeight;
            stopLoss = support - (patternHeight * 0.1);
            direction = "BULLISH";
        } else {
            target = support - patternHeight;
            stopLoss = resistance + (patternHeight * 0.1);
            direction = "BEARISH";
        }

        int confidence = calculateConfidence(candles, swingHighs, swingLows, resistance, support, lastCandle);

        int patternLength = swingHighs.get(swingHighs.size() - 1).getIndex() - swingLows.get(0).getIndex();

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
            .patternLength(Math.max(patternLength, 0))
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

        List<Candle> recentCandles = candles.subList(Math.max(0, candles.size() - 15), candles.size());

        // Lower highs
        if (patternUtils.isLowerHighs(recentCandles)) {
            confidence += 15;
        }

        // Higher lows
        if (patternUtils.isHigherLows(recentCandles)) {
            confidence += 15;
        }

        // Volume
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() > avgVolume * (1 + VOLUME_THRESHOLD)) {
            confidence += 20;
        } else if (lastCandle.getVolume() > avgVolume) {
            confidence += 10;
        }

        // Breakout confirmation strength (direction already confirmed by caller)
        boolean breakoutAbove = lastCandle.getClose() > resistance;
        double breakoutPercent = breakoutAbove ?
            patternUtils.percentageDifference(resistance, lastCandle.getClose()) :
            patternUtils.percentageDifference(support, lastCandle.getClose());
        if (breakoutPercent > 1.0) confidence += 15;

        return Math.min(100, confidence);
    }
}