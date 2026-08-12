package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects the Double Bottom chart pattern.
 *
 * Pattern Characteristics:
 * - Two swing lows at approximately the same price level (within 1%)
 * - One swing high between the two lows (the neckline resistance)
 * - Strong downtrend before the first low
 * - Close above the neckline to confirm breakout
 * - Volume confirmation on breakout
 *
 * Signal: Bullish reversal - expect uptrend continuation
 * Target: Neckline + (neckline - first low)
 * Stop Loss: Below the second low
 */
@Component
@Slf4j
public class DoubleBottomDetector implements PatternDetector {

    private final PivotDetector pivotDetector;
    private final PatternUtils patternUtils;

    // Configuration constants
    private static final int LOOKBACK_PERIOD = 3; // multiplier applied to pivot bars for the prior-downtrend check window
    private static final int PIVOT_LEFT_BARS = 3;
    private static final int PIVOT_RIGHT_BARS = 3;
    private static final double DEPTH_TOLERANCE = 1.0; // 1% tolerance for valley depths
    private static final double VOLUME_THRESHOLD_PERCENT = 0.8; // 80% of average
    private static final double BREAKOUT_THRESHOLD = 0.5; // 0.5% minimum breakout

    public DoubleBottomDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }

    @Override
    public ChartPattern pattern() {
        return ChartPattern.DOUBLE_BOTTOM;
    }

    @Override
    public String description() {
        return "Two valleys at approximately equal depths after a downtrend, followed by breakout above neckline";
    }

    @Override
    public int getPriority() {
        return 60; // Higher priority for common reversal pattern
    }

    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return PatternResult.none();
        }

        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);

        if (swingHighs == null || swingLows == null || swingLows.size() < 2) {
            return PatternResult.none();
        }

        Candle lastCandle = candles.get(candles.size() - 1);

        for (int i = 0; i < swingLows.size() - 1; i++) {
            PivotPoint low1 = swingLows.get(i);
            PivotPoint low2 = swingLows.get(i + 1);

            if (!patternUtils.pricesApproximatelyEqual(low1.getPrice(), low2.getPrice(), DEPTH_TOLERANCE)) {
                continue;
            }

            if (!hasPriorDowntrend(candles, low1)) {
                continue;
            }

            PivotPoint neckline = findNecklineBetweenLows(swingHighs, low1.getIndex(), low2.getIndex());
            if (neckline == null) {
                continue;
            }

            if (lastCandle.getClose() <= neckline.getPrice()) {
                continue;
            }

            // Calculate targets and stop loss
            double lowPrice = Math.min(low1.getPrice(), low2.getPrice());
            double patternHeight = neckline.getPrice() - lowPrice;
            double target = neckline.getPrice() + patternHeight;
            double stopLoss = lowPrice - (patternHeight * 0.1); // 10% below second low

            // Calculate confidence
            int confidence = calculateConfidence(candles, low1, low2, neckline, lastCandle);

            return PatternResult.builder()
                .pattern(ChartPattern.DOUBLE_BOTTOM)
                .confidence(confidence)
                .breakoutPrice(lastCandle.getClose())
                .stopLoss(stopLoss)
                .target(target)
                .secondaryTarget(neckline.getPrice())
                .description("Bullish reversal pattern with two valleys at " + String.format("%.2f", lowPrice) +
                    " and neckline resistance at " + String.format("%.2f", neckline.getPrice()))
                .patternLength(low2.getIndex() - low1.getIndex())
                .direction("BULLISH")
                .riskRewardRatio(patternUtils.calculateRiskRewardRatio())
                .detectionTime(System.currentTimeMillis())
                .build();
        }

        return PatternResult.none();
    }

    /**
     * Confirms the "strong downtrend before the first low" precondition from the pattern
     * definition - without this, two similar-looking lows are just noise, not a reversal.
     */
    private boolean hasPriorDowntrend(List<Candle> candles, PivotPoint low1) {
        int lookbackCandles = LOOKBACK_PERIOD * PIVOT_LEFT_BARS;
        int from = low1.getIndex() - lookbackCandles;
        if (from < 0) {
            // Not enough history before the first low to confirm a prior downtrend
            return false;
        }
        List<Candle> priorWindow = candles.subList(from, low1.getIndex() + 1);
        return patternUtils.isDowntrend(priorWindow, priorWindow.size());
    }

    /**
     * Finds the highest pivot between two low pivots (the neckline resistance).
     */
    private PivotPoint findNecklineBetweenLows(List<PivotPoint> swingHighs, int lowIndex1, int lowIndex2) {
        PivotPoint neckline = null;
        for (PivotPoint high : swingHighs) {
            if (high.getIndex() > lowIndex1 && high.getIndex() < lowIndex2) {
                if (neckline == null || high.getPrice() > neckline.getPrice()) {
                    neckline = high;
                }
            }
        }
        return neckline;
    }

    /**
     * Calculates confidence score based on multiple factors. Contributions below are
     * additive and capped at 100 overall - the inline percentages are relative weights,
     * not literal point values.
     */
    private int calculateConfidence(List<Candle> candles, PivotPoint low1, PivotPoint low2,
                                     PivotPoint neckline, Candle breakoutCandle) {
        int confidence = 50; // Base confidence

        // Valley equality
        double valleyDiff = Math.abs(low1.getPrice() - low2.getPrice());
        double tolerance = low1.getPrice() * (DEPTH_TOLERANCE / 100.0);
        if (valleyDiff < tolerance * 0.5) {
            confidence += 15; // Very close lows
        } else {
            confidence += 10; // Within tolerance
        }

        // Volume confirmation
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (breakoutCandle.getVolume() > avgVolume * 1.2) {
            confidence += 20;
        } else if (breakoutCandle.getVolume() > avgVolume * VOLUME_THRESHOLD_PERCENT) {
            confidence += 10;
        }

        // Breakout confirmation
        double breakoutPercent = Math.abs(patternUtils.percentageDifference(neckline.getPrice(), breakoutCandle.getClose()));
        if (breakoutPercent > 1.0) {
            confidence += 20;
        } else if (breakoutPercent > BREAKOUT_THRESHOLD) {
            confidence += 10;
        }

        // Trend validation - confirms momentum has actually turned since the pattern formed
        if (patternUtils.isUptrend(candles, 10)) {
            confidence += 20;
        }

        // ATR confirmation
        double atr = patternUtils.calculateATR(candles, 14);
        if (atr > 0 && breakoutCandle.getRange() > atr * 0.5) {
            confidence += 10;
        }

        return Math.min(100, confidence);
    }
}