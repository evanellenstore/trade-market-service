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
    public boolean detect(List<Candle> candles) {
        if (candles.size() < 30) {
            return false;
        }
        
        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);
        
        // Need at least 3 highs (shoulders and head) and 2 lows (neckline points)
        if (swingHighs.size() < 3 || swingLows.size() < 2) {
            return false;
        }
        
        // Look for three consecutive highs: left shoulder, head, right shoulder
        for (int i = 0; i < swingHighs.size() - 2; i++) {
            PivotPoint leftShoulder = swingHighs.get(i);
            PivotPoint head = swingHighs.get(i + 1);
            PivotPoint rightShoulder = swingHighs.get(i + 2);
            
            // Head must be significantly higher than shoulders
            if (!isValidHeadHeight(leftShoulder.getPrice(), head.getPrice(), rightShoulder.getPrice())) {
                continue;
            }
            
            // Shoulders should be approximately equal
            if (!patternUtils.pricesApproximatelyEqual(leftShoulder.getPrice(), rightShoulder.getPrice(), SHOULDER_TOLERANCE)) {
                continue;
            }
            
            // Find neckline pivots (valleys between shoulders and head)
            List<PivotPoint> necklinePivots = findNecklinePivots(swingLows, leftShoulder.getIndex(), head.getIndex(), rightShoulder.getIndex());
            if (necklinePivots.size() < 2) {
                continue;
            }
            
            PivotPoint neckline1 = necklinePivots.get(0);
            PivotPoint neckline2 = necklinePivots.get(1);
            
            // Check for breakdown confirmation
            Candle lastCandle = candles.get(candles.size() - 1);
            double necklineLevel = Math.max(neckline1.getPrice(), neckline2.getPrice());
            
            if (lastCandle.getClose() >= necklineLevel) {
                continue; // Not broken below neckline yet
            }
            
            // Volume confirmation
            double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
            if (lastCandle.getVolume() < avgVolume * VOLUME_THRESHOLD_PERCENT) {
                log.debug("H&S: Insufficient volume");
                continue;
            }
            
            log.info("Head and Shoulders detected: LS={}, Head={}, RS={}, Neckline={}", 
                String.format("%.2f", leftShoulder.getPrice()),
                String.format("%.2f", head.getPrice()),
                String.format("%.2f", rightShoulder.getPrice()),
                String.format("%.2f", necklineLevel));
            
            return true;
        }
        
        return false;
    }
    
    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        if (!detect(candles)) {
            return PatternResult.none();
        }
        
        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);
        
        for (int i = 0; i < swingHighs.size() - 2; i++) {
            PivotPoint leftShoulder = swingHighs.get(i);
            PivotPoint head = swingHighs.get(i + 1);
            PivotPoint rightShoulder = swingHighs.get(i + 2);
            
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
            
            Candle lastCandle = candles.get(candles.size() - 1);
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
     * Validates that head is significantly higher than both shoulders.
     */
    private boolean isValidHeadHeight(double leftShoulderPrice, double headPrice, double rightShoulderPrice) {
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
        } else if (breakoutCandle.getVolume() > avgVolume * 0.8) {
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
