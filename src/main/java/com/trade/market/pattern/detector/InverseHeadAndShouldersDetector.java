package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects the Inverse Head and Shoulders chart pattern.
 * 
 * Pattern Characteristics:
 * - Three valleys: left shoulder, head (lowest), right shoulder
 * - Left shoulder and right shoulder at approximately equal depths (within 2%)
 * - Head significantly lower than shoulders (at least 2%)
 * - Two peaks form the neckline (resistance line), which must themselves be
 *   roughly level (within 1.5x the shoulder tolerance)
 * - Close above neckline confirms the bullish reversal, provided price does
 *   not close back below the head first (which invalidates the pattern)
 * - Volume increases on neckline break above
 * 
 * Signal: Bullish reversal - strong uptrend expected
 * Target: Neckline + (neckline - head)
 * Stop Loss: Below the head
 *
 * Note: among multiple valid triples of swing lows, the detector prefers the
 * most recent one (closest to the end of the candle series), since that is
 * the pattern most relevant to a live/current detection use case.
 */
@Component
@Slf4j
public class InverseHeadAndShouldersDetector implements PatternDetector {
    
    private final PivotDetector pivotDetector;
    private final PatternUtils patternUtils;
    
    // Configuration constants
    private static final int PIVOT_LEFT_BARS = 3;
    private static final int PIVOT_RIGHT_BARS = 3;
    private static final double SHOULDER_TOLERANCE = 2.0;
    private static final double NECKLINE_TOLERANCE = SHOULDER_TOLERANCE * 1.5; // neckline peaks must be roughly level
    private static final double HEAD_DEPTH_MIN = 2.0; // Head must be 2% lower than shoulders
    private static final double VOLUME_BREAKOUT_STRONG_MULTIPLIER = 1.2;
    private static final double VOLUME_BREAKOUT_MIN_MULTIPLIER = 0.8;
    private static final double BREAKOUT_THRESHOLD = 0.5;
    
    public InverseHeadAndShouldersDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }
    
    @Override
    public ChartPattern pattern() {
        return ChartPattern.INVERSE_HEAD_AND_SHOULDERS;
    }
    
    @Override
    public String description() {
        return "Three valleys with middle valley (head) lowest, forming strong bullish reversal pattern";
    }
    
    @Override
    public int getPriority() {
        return 70; // High priority - mirror of H&S pattern
    }
    
    
    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);

        // Walk from the most recent triple of swing lows backwards, so that if
        // multiple valid patterns exist in the series we surface the freshest one.
        for (int i = swingLows.size() - 3; i >= 0; i--) {
            PivotPoint leftShoulder = swingLows.get(i);
            PivotPoint head = swingLows.get(i + 1);
            PivotPoint rightShoulder = swingLows.get(i + 2);

            if (!isValidHeadDepth(leftShoulder.getPrice(), head.getPrice(), rightShoulder.getPrice())) {
                continue;
            }

            if (!patternUtils.pricesApproximatelyEqual(leftShoulder.getPrice(), rightShoulder.getPrice(), SHOULDER_TOLERANCE)) {
                continue;
            }

            List<PivotPoint> necklinePivots = findNecklinePivots(swingHighs, leftShoulder.getIndex(), head.getIndex(), rightShoulder.getIndex());
            if (necklinePivots.size() < 2) {
                continue;
            }

            PivotPoint neckline1 = necklinePivots.get(0);
            PivotPoint neckline2 = necklinePivots.get(1);

            // Reject necklines whose two peaks are too far apart in price to be
            // considered a coherent resistance line.
            if (!patternUtils.pricesApproximatelyEqual(neckline1.getPrice(), neckline2.getPrice(), NECKLINE_TOLERANCE)) {
                continue;
            }

            double necklineLevel = Math.max(neckline1.getPrice(), neckline2.getPrice());

            Candle breakoutCandle = findValidBreakoutCandle(candles, rightShoulder.getIndex() + 1, head.getPrice(), necklineLevel);
            if (breakoutCandle == null) {
                continue;
            }

            // Calculate targets
            double patternHeight = necklineLevel - head.getPrice();
            double target = necklineLevel + patternHeight;
            double stopLoss = head.getPrice() - (patternHeight * 0.1);

            int confidence = calculateConfidence(candles, leftShoulder, head, rightShoulder, neckline1, neckline2, breakoutCandle);

            return PatternResult.builder()
                .pattern(ChartPattern.INVERSE_HEAD_AND_SHOULDERS)
                .patternDetected(true)
                .confidence(confidence)
                .breakoutPrice(breakoutCandle.getClose())
                .stopLoss(stopLoss)
                .target(target)
                .secondaryTarget(necklineLevel)
                .description("Inverse H&S: Left Shoulder=" + String.format("%.2f", leftShoulder.getPrice()) +
                    ", Head=" + String.format("%.2f", head.getPrice()) +
                    ", Right Shoulder=" + String.format("%.2f", rightShoulder.getPrice()) +
                    ", Neckline=" + String.format("%.2f", necklineLevel))
                .patternLength(rightShoulder.getIndex() - leftShoulder.getIndex())
                .direction("BULLISH")
                .riskRewardRatio(patternUtils.calculateRiskRewardRatio())
                .detectionTime(System.currentTimeMillis())
                .build();
        }

        return PatternResult.none();
    }
    
    /**
     * Validates that head is significantly lower than both shoulders.
     */
    private boolean isValidHeadDepth(double leftShoulderPrice, double headPrice, double rightShoulderPrice) {
        double avgShoulder = (leftShoulderPrice + rightShoulderPrice) / 2.0;
        double headDepth = patternUtils.percentageDifference(headPrice, avgShoulder);
        return headDepth >= HEAD_DEPTH_MIN;
    }
    
    /**
     * Finds the two neckline peaks between the three valleys.
     */
    private List<PivotPoint> findNecklinePivots(List<PivotPoint> swingHighs, int leftShoulderIdx, int headIdx, int rightShoulderIdx) {
        List<PivotPoint> necklines = new ArrayList<>();
        
        PivotPoint peak1 = null;
        for (PivotPoint high : swingHighs) {
            if (high.getIndex() > leftShoulderIdx && high.getIndex() < headIdx) {
                if (peak1 == null || high.getPrice() > peak1.getPrice()) {
                    peak1 = high;
                }
            }
        }
        
        PivotPoint peak2 = null;
        for (PivotPoint high : swingHighs) {
            if (high.getIndex() > headIdx && high.getIndex() < rightShoulderIdx) {
                if (peak2 == null || high.getPrice() > peak2.getPrice()) {
                    peak2 = high;
                }
            }
        }
        
        if (peak1 != null) necklines.add(peak1);
        if (peak2 != null) necklines.add(peak2);
        
        return necklines;
    }
    
    /**
     * Finds the first candle after the right shoulder that closes above the neckline
     * level, provided price does not close back below the head first. A close below
     * the head before the breakout invalidates the pattern (the "head" is no longer
     * the lowest point), so the pattern is rejected rather than matched against a
     * breakout that may occur much later under a different market structure.
     */
    private Candle findValidBreakoutCandle(List<Candle> candles, int startIndex, double headPrice, double necklineLevel) {
        for (int i = Math.max(0, startIndex); i < candles.size(); i++) {
            Candle candle = candles.get(i);

            if (candle.getClose() < headPrice) {
                return null; // pattern invalidated before a valid breakout occurred
            }

            if (candle.getClose() > necklineLevel) {
                return candle;
            }
        }
        return null;
    }
    
    /**
     * Calculates confidence score.
     */
    private int calculateConfidence(List<Candle> candles, PivotPoint leftShoulder, PivotPoint head,
                                    PivotPoint rightShoulder, PivotPoint neckline1, PivotPoint neckline2,
                                    Candle breakoutCandle) {
        int confidence = 60;
        
        // Shoulder equality
        double shoulderDiff = Math.abs(leftShoulder.getPrice() - rightShoulder.getPrice());
        double avgShoulder = (leftShoulder.getPrice() + rightShoulder.getPrice()) / 2.0;
        if (shoulderDiff < avgShoulder * 0.01) {
            confidence += 20;
        } else if (shoulderDiff < avgShoulder * 0.02) {
            confidence += 15;
        } else {
            confidence += 10;
        }
        
        // Head depth
        double headDepth = patternUtils.percentageDifference(head.getPrice(), avgShoulder);
        if (headDepth > 3.0) {
            confidence += 20;
        } else {
            confidence += 15;
        }
        
        // Volume
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (breakoutCandle.getVolume() > avgVolume * VOLUME_BREAKOUT_STRONG_MULTIPLIER) {
            confidence += 20;
        } else if (breakoutCandle.getVolume() > avgVolume * VOLUME_BREAKOUT_MIN_MULTIPLIER) {
            confidence += 10;
        }
        
        // Neckline quality
        double necklineLevel = Math.max(neckline1.getPrice(), neckline2.getPrice());
        double breakoutPercent = Math.abs(patternUtils.percentageDifference(necklineLevel, breakoutCandle.getClose()));
        if (breakoutPercent > 1.0) {
            confidence += 20;
        } else if (breakoutPercent > BREAKOUT_THRESHOLD) {
            confidence += 10;
        }
        
        return Math.min(100, confidence);
    }
}