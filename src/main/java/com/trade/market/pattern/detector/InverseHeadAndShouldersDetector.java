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
 * - Two peaks form the neckline (resistance line)
 * - Close above neckline confirms the bullish reversal
 * - Volume decreases into valleys
 * - Volume increases on neckline break above
 * 
 * Signal: Bullish reversal - strong uptrend expected
 * Target: Neckline + (neckline - head)
 * Stop Loss: Below the head
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
    private static final double HEAD_DEPTH_MIN = 2.0; // Head must be 2% lower than shoulders
    private static final double VOLUME_THRESHOLD_PERCENT = 0.8;
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
    public boolean detect(List<Candle> candles) {
        if (candles.size() < 30) {
            return false;
        }
        
        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);
        
        // Need at least 3 lows and 2 highs
        if (swingLows.size() < 3 || swingHighs.size() < 2) {
            return false;
        }
        
        // Look for three consecutive lows
        for (int i = 0; i < swingLows.size() - 2; i++) {
            PivotPoint leftShoulder = swingLows.get(i);
            PivotPoint head = swingLows.get(i + 1);
            PivotPoint rightShoulder = swingLows.get(i + 2);
            
            // Head must be significantly lower than shoulders
            if (!isValidHeadDepth(leftShoulder.getPrice(), head.getPrice(), rightShoulder.getPrice())) {
                continue;
            }
            
            // Shoulders should be approximately equal
            if (!patternUtils.pricesApproximatelyEqual(leftShoulder.getPrice(), rightShoulder.getPrice(), SHOULDER_TOLERANCE)) {
                continue;
            }
            
            // Find neckline pivots (peaks between shoulders and head)
            List<PivotPoint> necklinePivots = findNecklinePivots(swingHighs, leftShoulder.getIndex(), head.getIndex(), rightShoulder.getIndex());
            if (necklinePivots.size() < 2) {
                continue;
            }
            
            PivotPoint neckline1 = necklinePivots.get(0);
            PivotPoint neckline2 = necklinePivots.get(1);
            
            // Check for breakout confirmation
            Candle lastCandle = candles.get(candles.size() - 1);
            double necklineLevel = Math.min(neckline1.getPrice(), neckline2.getPrice());
            
            if (lastCandle.getClose() <= necklineLevel) {
                continue; // Not broken above neckline
            }
            
            // Volume confirmation
            double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
            if (lastCandle.getVolume() < avgVolume * VOLUME_THRESHOLD_PERCENT) {
                log.debug("Inverse H&S: Insufficient volume");
                continue;
            }
            
            log.info("Inverse Head and Shoulders detected: LS={}, Head={}, RS={}, Neckline={}", 
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
        
        for (int i = 0; i < swingLows.size() - 2; i++) {
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
            double necklineLevel = Math.min(neckline1.getPrice(), neckline2.getPrice());
            
            Candle lastCandle = candles.get(candles.size() - 1);
            if (lastCandle.getClose() <= necklineLevel) {
                continue;
            }
            
            // Calculate targets
            double patternHeight = necklineLevel - head.getPrice();
            double target = necklineLevel + patternHeight;
            double stopLoss = head.getPrice() - (patternHeight * 0.1);
            
            int confidence = calculateConfidence(candles, leftShoulder, head, rightShoulder, neckline1, neckline2, lastCandle);
            
            return PatternResult.builder()
                .pattern(ChartPattern.INVERSE_HEAD_AND_SHOULDERS)
                .confidence(confidence)
                .breakoutPrice(lastCandle.getClose())
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
        if (breakoutCandle.getVolume() > avgVolume * 1.2) {
            confidence += 20;
        } else if (breakoutCandle.getVolume() > avgVolume * 0.8) {
            confidence += 10;
        }
        
        // Neckline quality
        double necklineLevel = Math.min(neckline1.getPrice(), neckline2.getPrice());
        double breakoutPercent = Math.abs(patternUtils.percentageDifference(necklineLevel, breakoutCandle.getClose()));
        if (breakoutPercent > 1.0) {
            confidence += 20;
        } else if (breakoutPercent > BREAKOUT_THRESHOLD) {
            confidence += 10;
        }
        
        return Math.min(100, confidence);
    }
}
