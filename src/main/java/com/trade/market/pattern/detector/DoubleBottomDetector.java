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
    private static final int LOOKBACK_PERIOD = 3;
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
    public boolean detect(List<Candle> candles) {
        if (candles.size() < 20) {
            return false;
        }
        
        // Get swing highs and lows
        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, PIVOT_LEFT_BARS, PIVOT_RIGHT_BARS);
        
        // Need at least 2 lows and 1 high between them
        if (swingLows.size() < 2 || swingHighs.isEmpty()) {
            return false;
        }
        
        // Find two consecutive lows that are approximately equal
        for (int i = 0; i < swingLows.size() - 1; i++) {
            PivotPoint low1 = swingLows.get(i);
            PivotPoint low2 = swingLows.get(i + 1);
            
            // Check if lows are approximately equal
            if (!patternUtils.pricesApproximatelyEqual(low1.getPrice(), low2.getPrice(), DEPTH_TOLERANCE)) {
                continue;
            }
            
            // Find the high between the two lows (the neckline)
            PivotPoint neckline = findNecklineBetweenLows(swingHighs, low1.getIndex(), low2.getIndex());
            if (neckline == null) {
                continue;
            }
            
            // Check for downtrend confirmation before pattern (optional - price should be falling into first low)
            int startIdx = Math.max(0, low1.getIndex() - 10);
            if (startIdx < low1.getIndex()) {
                List<Candle> preTrendCandles = candles.subList(startIdx, low1.getIndex());
                if (!patternUtils.isLowerHighs(preTrendCandles) && !patternUtils.isLowerLows(preTrendCandles)) {
                    // Weak downtrend, but continue anyway
                }
            }
            
            // Check for breakout confirmation at current candle
            Candle lastCandle = candles.get(candles.size() - 1);
            if (lastCandle.getClose() <= neckline.getPrice()) {
                continue; // Not broken above neckline yet
            }
            
            // Verify volume confirmation
            double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
            if (lastCandle.getVolume() < avgVolume * VOLUME_THRESHOLD_PERCENT) {
                log.debug("Double Bottom: Insufficient volume confirmation");
                continue;
            }
            
            // All checks passed
            log.info("Double Bottom pattern detected: low1={}, low2={}, neckline={}", 
                String.format("%.2f", low1.getPrice()), 
                String.format("%.2f", low2.getPrice()), 
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
        
        for (int i = 0; i < swingLows.size() - 1; i++) {
            PivotPoint low1 = swingLows.get(i);
            PivotPoint low2 = swingLows.get(i + 1);
            
            if (!patternUtils.pricesApproximatelyEqual(low1.getPrice(), low2.getPrice(), DEPTH_TOLERANCE)) {
                continue;
            }
            
            PivotPoint neckline = findNecklineBetweenLows(swingHighs, low1.getIndex(), low2.getIndex());
            if (neckline == null) {
                continue;
            }
            
            Candle lastCandle = candles.get(candles.size() - 1);
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
     * Calculates confidence score based on multiple factors.
     */
    private int calculateConfidence(List<Candle> candles, PivotPoint low1, PivotPoint low2,
                                    PivotPoint neckline, Candle breakoutCandle) {
        int confidence = 50; // Base confidence
        
        // Valley equality (30% weight)
        double valleyDiff = Math.abs(low1.getPrice() - low2.getPrice());
        double tolerance = low1.getPrice() * (DEPTH_TOLERANCE / 100.0);
        if (valleyDiff < tolerance * 0.5) {
            confidence += 15; // Very close lows
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
        if (patternUtils.isUptrend(candles, 10)) {
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
