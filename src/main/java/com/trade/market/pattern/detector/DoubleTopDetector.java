package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects the Double Top chart pattern.
 * 
 * Pattern Characteristics:
 * - Two swing highs at approximately the same price level (within 1%)
 * - One swing low between the two highs (the neckline support)
 * - Strong uptrend before the first high
 * - Close below the neckline to confirm breakout/reversal
 * - Volume confirmation on breakdown
 * 
 * Signal: Bearish reversal - expect downtrend continuation
 * Target: Price below neckline + (first high - neckline)
 * Stop Loss: Above the second high
 */
@Component
@Slf4j
public class DoubleTopDetector implements PatternDetector {
    
    private final PivotDetector pivotDetector;
    private final PatternUtils patternUtils;
    
    // Configuration constants
    private static final int PIVOT_LEFT_BARS = 3;
    private static final int PIVOT_RIGHT_BARS = 3;
    private static final double HEIGHT_TOLERANCE = 1.0; // 1% tolerance for peak heights
    private static final double VOLUME_THRESHOLD_PERCENT = 0.8; // 80% of average
    private static final double BREAKOUT_THRESHOLD = 0.5; // 0.5% minimum breakdown

    // Minimum bars required between the two peaks so we don't treat noise as a pattern.
    // (Replaces the old unused LOOKBACK_PERIOD constant.)
    private static final int MIN_PEAK_SPACING_BARS = 5;

    // How many bars after the second high we still consider a breakdown "live"/relevant.
    // Prevents an old, stale pair from being confirmed by unrelated recent price action.
    private static final int MAX_BARS_SINCE_SECOND_HIGH = 30;
    
    public DoubleTopDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }
    
    @Override
    public ChartPattern pattern() {
        return ChartPattern.DOUBLE_TOP;
    }
    
    @Override
    public String description() {
        return "Two peaks at approximately equal heights after an uptrend, followed by breakdown below neckline";
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

        if (swingHighs.size() < 2) {
            return PatternResult.none();
        }

        Candle lastCandle = candles.get(candles.size() - 1);
        int lastIndex = candles.size() - 1;

        // Iterate pairs from most recent to oldest so we return the freshest valid
        // pattern instead of the first (potentially stale) one found from the start.
        for (int i = swingHighs.size() - 2; i >= 0; i--) {
            PivotPoint high1 = swingHighs.get(i);
            PivotPoint high2 = swingHighs.get(i + 1);

            // Require the peaks to be meaningfully spaced apart, not adjacent noise.
            if (high2.getIndex() - high1.getIndex() < MIN_PEAK_SPACING_BARS) {
                continue;
            }

            if (!patternUtils.pricesApproximatelyEqual(high1.getPrice(), high2.getPrice(), HEIGHT_TOLERANCE)) {
                continue;
            }

            PivotPoint neckline = findNecklineBetweenHighs(swingLows, high1.getIndex(), high2.getIndex());
            if (neckline == null) {
                continue;
            }

            // Only treat this pair as "confirming now" if the second high is still
            // recent relative to the latest candle - otherwise a stale pair could be
            // falsely confirmed by unrelated, much later price action.
            if (lastIndex - high2.getIndex() > MAX_BARS_SINCE_SECOND_HIGH) {
                continue;
            }

            if (lastCandle.getClose() >= neckline.getPrice()) {
                continue;
            }

            // Calculate targets and stop loss
            double highPrice = Math.max(high1.getPrice(), high2.getPrice());
            double patternHeight = highPrice - neckline.getPrice();
            double target = neckline.getPrice() - patternHeight;
            double stopLoss = highPrice + (patternHeight * 0.1); // 10% above second high

            // Calculate confidence
            int confidence = calculateConfidence(candles, high1, high2, neckline, lastCandle);

            return PatternResult.builder()
                .pattern(ChartPattern.DOUBLE_TOP)
                .confidence(confidence)
                .breakoutPrice(lastCandle.getClose())
                .stopLoss(stopLoss)
                .target(target)
                .secondaryTarget(neckline.getPrice())
                .description("Bearish reversal pattern with two peaks at " + String.format("%.2f", highPrice) +
                    " and neckline support at " + String.format("%.2f", neckline.getPrice()))
                .patternLength(high2.getIndex() - high1.getIndex())
                .direction("BEARISH")
                .riskRewardRatio(patternUtils.calculateRiskRewardRatio())
                .detectionTime(System.currentTimeMillis())
                .build();
        }

        return PatternResult.none();
    }
    
    /**
     * Finds the lowest pivot between two high pivots (the neckline).
     */
    private PivotPoint findNecklineBetweenHighs(List<PivotPoint> swingLows, int highIndex1, int highIndex2) {
        PivotPoint neckline = null;
        for (PivotPoint low : swingLows) {
            if (low.getIndex() > highIndex1 && low.getIndex() < highIndex2) {
                if (neckline == null || low.getPrice() < neckline.getPrice()) {
                    neckline = low;
                }
            }
        }
        return neckline;
    }
    
    /**
     * Calculates confidence score based on multiple factors.
     */
    private int calculateConfidence(List<Candle> candles, PivotPoint high1, PivotPoint high2, 
                                    PivotPoint neckline, Candle breakoutCandle) {
        int confidence = 50; // Base confidence
        
        // Peak equality (30% weight)
        double peakDiff = Math.abs(high1.getPrice() - high2.getPrice());
        double tolerance = high1.getPrice() * (HEIGHT_TOLERANCE / 100.0);
        if (peakDiff < tolerance * 0.5) {
            confidence += 15; // Very close peaks
        } else {
            confidence += 10; // Within tolerance
        }
        
        // Volume confirmation (20% weight)
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (breakoutCandle.getVolume() > avgVolume * 1.2) {
            confidence += 20;
        } else if (breakoutCandle.getVolume() > avgVolume * VOLUME_THRESHOLD_PERCENT) {
            confidence += 10;
        }
        
        // Breakout confirmation (20% weight)
        double breakoutPercent = Math.abs(patternUtils.percentageDifference(neckline.getPrice(), breakoutCandle.getClose()));
        if (breakoutPercent > 1.0) {
            confidence += 20;
        } else if (breakoutPercent > BREAKOUT_THRESHOLD) {
            confidence += 10;
        }
        
        // Trend validation (20% weight)
        if (patternUtils.isDowntrend(candles, 10)) {
            confidence += 20;
        }
        
        // ATR confirmation (10% weight)
        double atr = patternUtils.calculateATR(candles, 14);
        if (atr > 0 && breakoutCandle.getRange() > atr * 0.5) {
            confidence += 10;
        }
        
        return Math.min(100, confidence);
    }
}