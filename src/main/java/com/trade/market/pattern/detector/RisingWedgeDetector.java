package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
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
    private static final double VOLUME_THRESHOLD = 0.8;
    private static final double BREAKOUT_THRESHOLD = 0.5;
    
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
    public boolean detect(List<Candle> candles) {
        if (candles.size() < MIN_PATTERN_CANDLES) {
            return false;
        }
        
        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, 2, 2);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, 2, 2);
        
        if (swingHighs.size() < MIN_TOUCHES || swingLows.size() < MIN_TOUCHES) {
            return false;
        }
        
        // Check for higher highs
        if (!patternUtils.isHigherHighs(candles.subList(Math.max(0, candles.size() - 15), candles.size()))) {
            return false;
        }
        
        // Check for higher lows
        if (!patternUtils.isHigherLows(candles.subList(Math.max(0, candles.size() - 15), candles.size()))) {
            return false;
        }
        
        // Check for convergence
        if (!isConverging(swingHighs, swingLows)) {
            return false;
        }
        
        // Check for breakdown confirmation
        Candle lastCandle = candles.get(candles.size() - 1);
        double support = patternUtils.getLowestPivot(swingLows).getPrice();
        
        if (lastCandle.getClose() >= support) {
            return false;
        }
        
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() < avgVolume * VOLUME_THRESHOLD) {
            return false;
        }
        
        log.info("Rising Wedge detected: Breakdown below support={}", 
            String.format("%.2f", support));
        
        return true;
    }
    
    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        if (!detect(candles)) {
            return PatternResult.none();
        }
        
        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, 2, 2);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, 2, 2);
        
        double resistance = patternUtils.getHighestPivot(swingHighs).getPrice();
        double support = patternUtils.getLowestPivot(swingLows).getPrice();
        double wedgeHeight = resistance - support;
        
        Candle lastCandle = candles.get(candles.size() - 1);
        
        double target = support - wedgeHeight;
        double stopLoss = resistance + (wedgeHeight * 0.1);
        
        int confidence = calculateConfidence(candles, swingHighs, swingLows, resistance, support, lastCandle);
        
        return PatternResult.builder()
            .pattern(ChartPattern.RISING_WEDGE)
            .confidence(confidence)
            .breakoutPrice(lastCandle.getClose())
            .stopLoss(stopLoss)
            .target(target)
            .secondaryTarget(support)
            .description("Rising Wedge: Resistance=" + String.format("%.2f", resistance) +
                ", Support=" + String.format("%.2f", support))
            .patternLength(swingHighs.get(swingHighs.size() - 1).getIndex() - swingLows.get(0).getIndex())
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
                                    List<PivotPoint> swingLows, double resistance, double support,
                                    Candle lastCandle) {
        int confidence = 50;
        
        if (swingHighs.size() >= 4) confidence += 10;
        if (swingLows.size() >= 4) confidence += 10;
        
        if (patternUtils.isHigherHighs(candles.subList(Math.max(0, candles.size() - 15), candles.size()))) {
            confidence += 15;
        }
        
        if (patternUtils.isHigherLows(candles.subList(Math.max(0, candles.size() - 15), candles.size()))) {
            confidence += 15;
        }
        
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() > avgVolume * 1.2) {
            confidence += 20;
        }
        
        double breakdownPercent = Math.abs(patternUtils.percentageDifference(support, lastCandle.getClose()));
        if (breakdownPercent > 1.0) confidence += 15;
        
        return Math.min(100, confidence);
    }
}
