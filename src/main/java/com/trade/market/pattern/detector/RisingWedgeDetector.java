package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects the Rising Wedge chart pattern.
 * 
 * Pattern Characteristics:
 * - Higher highs (upward sloping resistance)
 * - Higher lows (upward sloping support, but support slope is steeper than resistance)
 * - Both lines converge upward (converging wedge shape)
 * - Volume decreases as price rises (weakening momentum)
 * - Bearish breakdown expected below support
 * 
 * Signal: Bearish reversal despite higher prices - often seen after strong uptrends
 * Target: Support level - wedge height
 * Stop Loss: Above the last high
 */
@Component
@Slf4j
public class RisingWedgeDetector implements PatternDetector {

    private final PivotDetector pivotDetector;
    private final PatternUtils patternUtils;

    // Configuration constants
    private static final int MIN_PATTERN_CANDLES = 15;
    private static final double CONVERGENCE_THRESHOLD = 0.8;
    private static final int MIN_TOUCHES = 3;
    private static final double VOLUME_THRESHOLD = 0.8; // min volume multiple above average, as a fraction added on top
    private static final double BREAKOUT_THRESHOLD = 0.5; // min % close must clear support by, downward

    public RisingWedgeDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }

    @Override
    public ChartPattern pattern() {
        return ChartPattern.RISING_WEDGE;
    }

    @Override
    public String description() {
        return "Higher highs and higher lows with converging trendlines - bearish despite uptrend";
    }

    @Override
    public int getPriority() {
        return 55;
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
        double wedgeHeight = resistance - support;

        if (wedgeHeight <= 0) {
            return null;
        }

        Candle lastCandle = candles.get(candles.size() - 1);

        // Confirm an actual breakdown: close must clear support by at least BREAKOUT_THRESHOLD %, downward.
        double breakdownPct = patternUtils.percentageDifference(support, lastCandle.getClose());
        boolean brokeDown = lastCandle.getClose() < support && breakdownPct >= BREAKOUT_THRESHOLD;
        if (!brokeDown) {
            return null;
        }

        double target = support - wedgeHeight;
        double stopLoss = resistance + (wedgeHeight * 0.1);

        int confidence = calculateConfidence(candles, swingHighs, swingLows, breakdownPct, lastCandle);

        int patternLength = swingHighs.get(swingHighs.size() - 1).getIndex() - swingLows.get(0).getIndex();

        return PatternResult.builder()
            .pattern(ChartPattern.RISING_WEDGE)
            .confidence(confidence)
            .breakoutPrice(lastCandle.getClose())
            .stopLoss(stopLoss)
            .target(target)
            .secondaryTarget(support)
            .description("Rising Wedge: Resistance=" + String.format("%.2f", resistance) +
                ", Support=" + String.format("%.2f", support))
            .patternLength(Math.max(patternLength, 0))
            .direction("BEARISH")
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

        return finalSpread < initialSpread * CONVERGENCE_THRESHOLD;
    }

    private int calculateConfidence(List<Candle> candles, List<PivotPoint> swingHighs,
                                    List<PivotPoint> swingLows, double breakdownPct,
                                    Candle lastCandle) {
        int confidence = 50;

        if (swingHighs.size() >= 4) confidence += 10;
        if (swingLows.size() >= 4) confidence += 10;

        List<Candle> recentCandles = candles.subList(Math.max(0, candles.size() - 15), candles.size());

        if (patternUtils.isHigherHighs(recentCandles)) {
            confidence += 15;
        }

        if (patternUtils.isHigherLows(recentCandles)) {
            confidence += 15;
        }

        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() > avgVolume * (1 + VOLUME_THRESHOLD)) {
            confidence += 20;
        } else if (lastCandle.getVolume() > avgVolume) {
            confidence += 10;
        }

        // breakdownPct already confirmed positive (i.e. below support) by caller
        if (breakdownPct > 1.0) confidence += 15;

        return Math.min(100, confidence);
    }
}