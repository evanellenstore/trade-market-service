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
    private static final int LOOKBACK_PERIOD = 3;
    private static final int PIVOT_LEFT_BARS = 3;
    private static final int PIVOT_RIGHT_BARS = 3;
    private static final double HEIGHT_TOLERANCE = 1.0; // 1% tolerance for peak heights
    private static final double VOLUME_THRESHOLD_PERCENT = 0.8; // 80% of average
    private static final double BREAKOUT_THRESHOLD = 0.5; // 0.5% minimum breakdown
    
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
    public boolean detect(List<Candle> candles) {
        if (candles.size() < 20) {
            return false;
        }
        
        // Get swing highs and lows
        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);
        
        // Need at least 2 highs and 1 low between them
        if (swingHighs.size() < 2 || swingLows.isEmpty()) {
            return false;
        }
        
        // Find two consecutive highs that are approximately equal
        for (int i = 0; i < swingHighs.size() - 1; i++) {
            PivotPoint high1 = swingHighs.get(i);
            PivotPoint high2 = swingHighs.get(i + 1);
            
            // Check if highs are approximately equal
            if (!patternUtils.pricesApproximatelyEqual(high1.getPrice(), high2.getPrice(), HEIGHT_TOLERANCE)) {
                continue;
            }
            
            // Find the low between the two highs
            PivotPoint neckline = findNecklineBetweenHighs(swingLows, high1.getIndex(), high2.getIndex());
            if (neckline == null) {
                continue;
            }
            
            // Check for downtrend before first high
            if (!patternUtils.isDowntrend(candles.subList(Math.max(0, high1.getIndex() - LOOKBACK_PERIOD), high1.getIndex() + 1), LOOKBACK_PERIOD)) {
                // It's actually an uptrend before the first high (as expected for double top)
                // We'll verify differently: check if we had an uptrend before reaching first high
                int startIdx = Math.max(0, high1.getIndex() - 10);
                if (startIdx < high1.getIndex()) {
                    List<Candle> preTrendCandles = candles.subList(startIdx, high1.getIndex());
                    if (!patternUtils.isHigherHighs(preTrendCandles) && !patternUtils.isHigherLows(preTrendCandles)) {
                        continue; // Not enough uptrend confirmation
                    }
                }
            }
            
            // Check for breakdown confirmation at current candle
            Candle lastCandle = candles.get(candles.size() - 1);
            if (lastCandle.getClose() >= neckline.getPrice()) {
                continue; // Not broken below neckline yet
            }
            
            // Verify volume confirmation
            double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
            if (lastCandle.getVolume() < avgVolume * VOLUME_THRESHOLD_PERCENT) {
                log.debug("Double Top: Insufficient volume confirmation");
                continue;
            }
            
            // All checks passed
            log.info("Double Top pattern detected: high1={}, high2={}, neckline={}", 
                String.format("%.2f", high1.getPrice()), 
                String.format("%.2f", high2.getPrice()), 
                String.format("%.2f", neckline.getPrice()));
            
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
        
        for (int i = 0; i < swingHighs.size() - 1; i++) {
            PivotPoint high1 = swingHighs.get(i);
            PivotPoint high2 = swingHighs.get(i + 1);
            
            if (!patternUtils.pricesApproximatelyEqual(high1.getPrice(), high2.getPrice(), HEIGHT_TOLERANCE)) {
                continue;
            }
            
            PivotPoint neckline = findNecklineBetweenHighs(swingLows, high1.getIndex(), high2.getIndex());
            if (neckline == null) {
                continue;
            }
            
            Candle lastCandle = candles.get(candles.size() - 1);
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
        } else if (breakoutCandle.getVolume() > avgVolume * 0.8) {
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
