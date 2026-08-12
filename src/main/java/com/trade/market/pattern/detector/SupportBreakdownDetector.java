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
    private static final double VOLUME_THRESHOLD = 0.8; // min volume multiple above average, as a fraction added on top
    private static final double BREAKOUT_THRESHOLD = 0.5; // min % close must clear support by, downward

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
    public PatternResult detectWithResult(List<Candle> candles) {
        if (candles == null || candles.size() < MIN_LOOKBACK) {
            return null;
        }

        List<Candle> lookbackCandles = candles.subList(
            Math.max(0, candles.size() - MIN_LOOKBACK), candles.size());

        List<PivotPoint> recentLows = pivotDetector.detectSwingLows(lookbackCandles, 2, 2);

        double support = findSupportLevel(candles, recentLows);
        if (support <= 0) {
            // No valid, sufficiently-tested support level found — no pattern.
            return null;
        }

        double resistance = lookbackCandles.stream().mapToDouble(Candle::getHigh).max().orElse(0);
        if (resistance <= support) {
            return null;
        }

        Candle lastCandle = candles.get(candles.size() - 1);

        // Confirm an actual breakdown: close must clear support by at least BREAKOUT_THRESHOLD %, downward.
        double breakdownPct = patternUtils.percentageDifference(support, lastCandle.getClose());
        boolean brokeDown = lastCandle.getClose() < support && breakdownPct >= BREAKOUT_THRESHOLD;
        if (!brokeDown) {
            return null;
        }

        double projectedDistance = resistance - support;
        double target = support - projectedDistance;
        double stopLoss = support + (projectedDistance * 0.1);

        int confidence = calculateConfidence(candles, breakdownPct, lastCandle);

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
        double bottomLevel = pivots.stream().mapToDouble(PivotPoint::getPrice).min().orElse(0);
        int touches = 0;

        for (PivotPoint pivot : pivots) {
            if (patternUtils.pricesApproximatelyEqual(pivot.getPrice(), bottomLevel, SUPPORT_TOLERANCE)) {
                touches++;
            }
        }

        return touches >= MIN_TOUCHES ? bottomLevel : 0;
    }

    private int calculateConfidence(List<Candle> candles, double breakdownPct, Candle lastCandle) {
        int confidence = 50;

        // Breakdown strength (already confirmed positive/below support by caller)
        if (breakdownPct > 1.5) confidence += 20;
        else if (breakdownPct > 0.8) confidence += 15;

        // Volume
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() > avgVolume * (1 + VOLUME_THRESHOLD)) {
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