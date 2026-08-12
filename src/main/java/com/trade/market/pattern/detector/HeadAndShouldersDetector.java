package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects the Head and Shoulders chart pattern.
 * 
 * Pattern Characteristics:
 * - Three peaks: left shoulder, head (highest), right shoulder
 * - Left shoulder and right shoulder at approximately equal heights (within 2%)
 * - Head significantly higher than shoulders (at least 2%)
 * - Two valleys form the neckline (support line connecting the two valleys)
 * - Close below neckline confirms the reversal
 * - Volume decreases on each peak (distribution)
 * - Volume increases on neckline break
 * 
 * Signal: Bearish reversal - strong downtrend expected
 * Target: Neckline - (head - neckline)
 * Stop Loss: Above the head
 */
@Component
@Slf4j
public class HeadAndShouldersDetector implements PatternDetector {
    
    private final PivotDetector pivotDetector;
    private final PatternUtils patternUtils;
    
    // Configuration constants
    private static final int PIVOT_LEFT_BARS = 3;
    private static final int PIVOT_RIGHT_BARS = 3;
    private static final double SHOULDER_TOLERANCE = 2.0; // 2% tolerance for shoulder heights
    private static final double HEAD_HEIGHT_MIN = 2.0; // Head must be 2% higher than shoulders
    private static final double VOLUME_THRESHOLD_PERCENT = 0.8;
    private static final double BREAKOUT_THRESHOLD = 0.5; // 0.5% minimum

    // Minimum bars required between each shoulder and the head, so adjacent
    // noise pivots don't get treated as a real three-peak pattern.
    private static final int MIN_PEAK_SPACING_BARS = 4;

    // How many bars after the right shoulder we still consider a neckline
    // break "live"/relevant, so a stale triple can't be confirmed by unrelated
    // much-later price action.
    private static final int MAX_BARS_SINCE_RIGHT_SHOULDER = 30;
    
    public HeadAndShouldersDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }
    
    @Override
    public ChartPattern pattern() {
        return ChartPattern.HEAD_AND_SHOULDERS;
    }
    
    @Override
    public String description() {
        return "Three peaks with middle peak (head) highest, forming strong bearish reversal pattern";
    }
    
    @Override
    public int getPriority() {
        return 70; // High priority - very reliable pattern
    }
    
    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            return PatternResult.none();
        }

        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);

        if (swingHighs.size() < 3) {
            return PatternResult.none();
        }

        Candle lastCandle = candles.get(candles.size() - 1);
        int lastIndex = candles.size() - 1;

        // Iterate triples from most recent to oldest so we return the freshest
        // valid pattern instead of the first (potentially stale) one found.
        for (int i = swingHighs.size() - 3; i >= 0; i--) {
            PivotPoint leftShoulder = swingHighs.get(i);
            PivotPoint head = swingHighs.get(i + 1);
            PivotPoint rightShoulder = swingHighs.get(i + 2);

            // Require meaningful spacing between the peaks.
            if (head.getIndex() - leftShoulder.getIndex() < MIN_PEAK_SPACING_BARS
                || rightShoulder.getIndex() - head.getIndex() < MIN_PEAK_SPACING_BARS) {
                continue;
            }

            if (!isValidHeadHeight(leftShoulder.getPrice(), head.getPrice(), rightShoulder.getPrice())) {
                continue;
            }
            
            if (!patternUtils.pricesApproximatelyEqual(leftShoulder.getPrice(), rightShoulder.getPrice(), SHOULDER_TOLERANCE)) {
                continue;
            }
            
            List<PivotPoint> necklinePivots = findNecklinePivots(swingLows, leftShoulder.getIndex(), head.getIndex(), rightShoulder.getIndex());
            if (necklinePivots.size() < 2) {
                continue;
            }
            
            PivotPoint neckline1 = necklinePivots.get(0);
            PivotPoint neckline2 = necklinePivots.get(1);
            double necklineLevel = Math.max(neckline1.getPrice(), neckline2.getPrice());

            // Only treat this triple as "confirming now" if the right shoulder is
            // still recent relative to the latest candle.
            if (lastIndex - rightShoulder.getIndex() > MAX_BARS_SINCE_RIGHT_SHOULDER) {
                continue;
            }
            
            if (lastCandle.getClose() >= necklineLevel) {
                continue;
            }
            
            // Calculate targets
            double patternHeight = head.getPrice() - necklineLevel;
            double target = necklineLevel - patternHeight;
            double stopLoss = head.getPrice() + (patternHeight * 0.1);
            
            int confidence = calculateConfidence(candles, leftShoulder, head, rightShoulder, neckline1, neckline2, lastCandle);
            
            return PatternResult.builder()
                .pattern(ChartPattern.HEAD_AND_SHOULDERS)
                .confidence(confidence)
                .breakoutPrice(lastCandle.getClose())
                .stopLoss(stopLoss)
                .target(target)
                .secondaryTarget(necklineLevel)
                .description("Head and Shoulders: Left Shoulder=" + String.format("%.2f", leftShoulder.getPrice()) +
                    ", Head=" + String.format("%.2f", head.getPrice()) +
                    ", Right Shoulder=" + String.format("%.2f", rightShoulder.getPrice()) +
                    ", Neckline=" + String.format("%.2f", necklineLevel))
                .patternLength(rightShoulder.getIndex() - leftShoulder.getIndex())
                .direction("BEARISH")
                .riskRewardRatio(patternUtils.calculateRiskRewardRatio())
                .detectionTime(System.currentTimeMillis())
                .build();
        }
        
        return PatternResult.none();
    }
    
    /**
     * Validates that the head is significantly higher than both shoulders
     * individually, not just higher than their average.
     */
    private boolean isValidHeadHeight(double leftShoulderPrice, double headPrice, double rightShoulderPrice) {
        if (headPrice <= leftShoulderPrice || headPrice <= rightShoulderPrice) {
            return false;
        }
        double avgShoulder = (leftShoulderPrice + rightShoulderPrice) / 2.0;
        double headExcess = patternUtils.percentageDifference(avgShoulder, headPrice);
        return headExcess >= HEAD_HEIGHT_MIN;
    }
    
    /**
     * Finds the two neckline valleys between the three peaks.
     */
    private List<PivotPoint> findNecklinePivots(List<PivotPoint> swingLows, int leftShoulderIdx, int headIdx, int rightShoulderIdx) {
        List<PivotPoint> necklines = new ArrayList<>();
        
        PivotPoint valley1 = null;
        for (PivotPoint low : swingLows) {
            if (low.getIndex() > leftShoulderIdx && low.getIndex() < headIdx) {
                if (valley1 == null || low.getPrice() < valley1.getPrice()) {
                    valley1 = low;
                }
            }
        }
        
        PivotPoint valley2 = null;
        for (PivotPoint low : swingLows) {
            if (low.getIndex() > headIdx && low.getIndex() < rightShoulderIdx) {
                if (valley2 == null || low.getPrice() < valley2.getPrice()) {
                    valley2 = low;
                }
            }
        }
        
        if (valley1 != null) necklines.add(valley1);
        if (valley2 != null) necklines.add(valley2);
        
        return necklines;
    }
    
    /**
     * Calculates confidence score.
     */
    private int calculateConfidence(List<Candle> candles, PivotPoint leftShoulder, PivotPoint head, 
                                    PivotPoint rightShoulder, PivotPoint neckline1, PivotPoint neckline2, 
                                    Candle breakoutCandle) {
        int confidence = 60; // Higher base for reliable pattern
        
        // Shoulder equality (20% weight)
        double shoulderDiff = Math.abs(leftShoulder.getPrice() - rightShoulder.getPrice());
        double avgShoulder = (leftShoulder.getPrice() + rightShoulder.getPrice()) / 2.0;
        if (shoulderDiff < avgShoulder * 0.01) {
            confidence += 20;
        } else if (shoulderDiff < avgShoulder * 0.02) {
            confidence += 15;
        } else {
            confidence += 10;
        }
        
        // Head height (20% weight)
        double headExcess = patternUtils.percentageDifference(avgShoulder, head.getPrice());
        if (headExcess > 3.0) {
            confidence += 20;
        } else {
            confidence += 15;
        }
        
        // Volume confirmation (20% weight)
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (breakoutCandle.getVolume() > avgVolume * 1.2) {
            confidence += 20;
        } else if (breakoutCandle.getVolume() > avgVolume * VOLUME_THRESHOLD_PERCENT) {
            confidence += 10;
        }
        
        // Neckline quality (20% weight)
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