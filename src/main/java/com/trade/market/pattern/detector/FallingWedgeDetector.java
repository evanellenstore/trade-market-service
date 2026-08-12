package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects the Falling Wedge chart pattern.
 * 
 * Pattern Characteristics:
 * - Lower highs (downward sloping resistance)
 * - Lower lows (downward sloping support, but resistance slope is steeper than support)
 * - Both lines converge downward (converging wedge shape)
 * - Volume decreases as price falls (weakening downtrend)
 * - Bullish breakout expected above resistance
 * 
 * Signal: Bullish reversal despite lower prices - often seen after strong downtrends
 * Target: Resistance level + wedge height
 * Stop Loss: Below the last low
 */
@Component
@Slf4j
public class FallingWedgeDetector implements PatternDetector {
    
    private final PivotDetector pivotDetector;
    private final PatternUtils patternUtils;
    
    // Configuration constants
    private static final int MIN_PATTERN_CANDLES = 15;
    private static final double CONVERGENCE_THRESHOLD = 0.8;
    private static final int MIN_TOUCHES = 3;
    private static final double VOLUME_THRESHOLD = 0.8;
    private static final double BREAKOUT_THRESHOLD = 0.5;
    
    public FallingWedgeDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }
    
    @Override
    public ChartPattern pattern() {
        return ChartPattern.FALLING_WEDGE;
    }
    
    @Override
    public String description() {
        return "Lower highs and lower lows with converging trendlines - bullish despite downtrend";
    }
    
    @Override
    public int getPriority() {
        return 55;
    }
    
    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        if (candles == null || candles.size() < MIN_PATTERN_CANDLES) {
            return PatternResult.none();
        }

        List<PivotPoint> swingHighs = pivotDetector.detectSwingHighs(candles, 2, 2);
        List<PivotPoint> swingLows = pivotDetector.detectSwingLows(candles, 2, 2);

        // Need enough touches on each side to call this a real wedge, not noise.
        if (swingHighs.size() < MIN_TOUCHES || swingLows.size() < MIN_TOUCHES) {
            return PatternResult.none();
        }

        PivotPoint highestPivot = patternUtils.getHighestPivot(swingHighs);
        PivotPoint lowestPivot = patternUtils.getLowestPivot(swingLows);
        if (highestPivot == null || lowestPivot == null) {
            return PatternResult.none();
        }

        double resistance = highestPivot.getPrice();
        double support = lowestPivot.getPrice();
        double wedgeHeight = resistance - support;
        if (wedgeHeight <= 0) {
            return PatternResult.none();
        }

        List<Candle> recentWindow = candles.subList(
            Math.max(0, candles.size() - MIN_PATTERN_CANDLES), candles.size());

        // Structural checks that were previously computed but never gated on.
        if (!patternUtils.isLowerHighs(recentWindow) || !patternUtils.isLowerLows(recentWindow)) {
            return PatternResult.none();
        }
        if (!isConverging(swingHighs, swingLows)) {
            return PatternResult.none();
        }

        Candle lastCandle = candles.get(candles.size() - 1);

        // Confirm an actual bullish breakout above resistance before calling this a signal.
        if (lastCandle.getClose() <= resistance) {
            return PatternResult.none();
        }
        double breakoutPercent = patternUtils.percentageDifference(resistance, lastCandle.getClose());
        if (breakoutPercent < BREAKOUT_THRESHOLD) {
            return PatternResult.none();
        }

        double target = resistance + wedgeHeight;
        double stopLoss = support - (wedgeHeight * 0.1);

        int confidence = calculateConfidence(candles, swingHighs, swingLows, resistance, support, lastCandle);

        int firstIndex = Math.min(swingHighs.get(0).getIndex(), swingLows.get(0).getIndex());
        int lastIndex = Math.max(
            swingHighs.get(swingHighs.size() - 1).getIndex(),
            swingLows.get(swingLows.size() - 1).getIndex());

        return PatternResult.builder()
            .pattern(ChartPattern.FALLING_WEDGE)
            .confidence(confidence)
            .breakoutPrice(lastCandle.getClose())
            .stopLoss(stopLoss)
            .target(target)
            .secondaryTarget(resistance)
            .description("Falling Wedge: Resistance=" + String.format("%.2f", resistance) +
                ", Support=" + String.format("%.2f", support))
            .patternLength(lastIndex - firstIndex)
            .direction("BULLISH")
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

        if (initialSpread <= 0) {
            return false;
        }
        
        return finalSpread < initialSpread * CONVERGENCE_THRESHOLD;
    }
    
    private int calculateConfidence(List<Candle> candles, List<PivotPoint> swingHighs,
                                    List<PivotPoint> swingLows, double resistance, double support,
                                    Candle lastCandle) {
        int confidence = 50;
        
        if (swingHighs.size() >= 4) confidence += 10;
        if (swingLows.size() >= 4) confidence += 10;
        
        if (patternUtils.isLowerHighs(candles.subList(Math.max(0, candles.size() - 15), candles.size()))) {
            confidence += 15;
        }
        
        if (patternUtils.isLowerLows(candles.subList(Math.max(0, candles.size() - 15), candles.size()))) {
            confidence += 15;
        }
        
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() > avgVolume * 1.2) {
            confidence += 20;
        } else if (lastCandle.getVolume() > avgVolume * VOLUME_THRESHOLD) {
            confidence += 10;
        }
        
        double breakoutPercent = Math.abs(patternUtils.percentageDifference(resistance, lastCandle.getClose()));
        if (breakoutPercent > 1.0) {
            confidence += 15;
        } else if (breakoutPercent > BREAKOUT_THRESHOLD) {
            confidence += 8;
        }
        
        return Math.min(100, confidence);
    }
}