package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects the Bull Flag chart pattern.
 * 
 * Pattern Characteristics:
 * - Strong flagpole: sharp, nearly vertical uptrend
 * - Flag body: consolidation with slight downtrend (retracement < 50%)
 * - Parallel channel forming the flag rectangle
 * - Breakout above flag resistance on high volume
 * - Second uptrend similar to initial flagpole
 * 
 * Signal: Bullish continuation - strong uptrend continuation
 * Target: Flagpole height projected from breakout
 * Stop Loss: Below flag support
 */
@Component
@Slf4j
public class BullFlagDetector implements PatternDetector {
    
    private final PivotDetector pivotDetector;
    private final PatternUtils patternUtils;
    
    // Configuration constants
    private static final int MIN_FLAGPOLE_CANDLES = 3;
    private static final int MIN_FLAG_CANDLES = 5;
    private static final double MAX_RETRACEMENT = 0.5; // 50% max retracement
    private static final double FLAGPOLE_MIN_UPTREND = 2.0; // 2% minimum
    private static final double VOLUME_THRESHOLD = 0.8;
    private static final double BREAKOUT_THRESHOLD = 0.5;
    
    public BullFlagDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }
    
    @Override
    public ChartPattern pattern() {
        return ChartPattern.BULL_FLAG;
    }
    
    @Override
    public String description() {
        return "Strong uptrend flagpole followed by rectangular consolidation (bull flag)";
    }
    
    @Override
    public int getPriority() {
        return 65;
    }
    
    @Override
    public boolean detect(List<Candle> candles) {
        if (candles.size() < MIN_FLAGPOLE_CANDLES + MIN_FLAG_CANDLES) {
            return false;
        }
        
        // Identify potential flagpole (last MIN_FLAGPOLE_CANDLES with strong uptrend)
        int flagpoleStart = Math.max(0, candles.size() - MIN_FLAGPOLE_CANDLES - MIN_FLAG_CANDLES);
        List<Candle> flagpoleCandles = candles.subList(flagpoleStart, flagpoleStart + MIN_FLAGPOLE_CANDLES);
        
        // Check for strong uptrend in flagpole
        double flagpoleMove = patternUtils.percentageDifference(
            flagpoleCandles.get(0).getLow(),
            flagpoleCandles.get(flagpoleCandles.size() - 1).getHigh()
        );
        
        if (flagpoleMove < FLAGPOLE_MIN_UPTREND) {
            return false;
        }
        
        // Check flag consolidation
        int flagStart = flagpoleStart + MIN_FLAGPOLE_CANDLES;
        List<Candle> flagCandles = candles.subList(flagStart, candles.size());
        
        if (flagCandles.size() < MIN_FLAG_CANDLES) {
            return false;
        }
        
        // Check for slight downtrend (retracement < 50%)
        double flagHigh = getHighest(flagCandles);
        double flagLow = getLowest(flagCandles);
        double retracement = (flagHigh - flagLow) / flagpoleMove;
        
        if (retracement > MAX_RETRACEMENT) {
            return false;
        }
        
        // Check for breakout
        Candle lastCandle = candles.get(candles.size() - 1);
        if (lastCandle.getClose() <= flagHigh) {
            return false;
        }
        
        // Volume confirmation
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() < avgVolume * VOLUME_THRESHOLD) {
            return false;
        }
        
        log.info("Bull Flag detected: Flagpole Move={}, Retracement={}", 
            String.format("%.2f%%", flagpoleMove),
            String.format("%.2f%%", retracement * 100));
        
        return true;
    }
    
    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        if (!detect(candles)) {
            return PatternResult.none();
        }
        
        int flagpoleStart = Math.max(0, candles.size() - MIN_FLAGPOLE_CANDLES - MIN_FLAG_CANDLES);
        List<Candle> flagpoleCandles = candles.subList(flagpoleStart, flagpoleStart + MIN_FLAGPOLE_CANDLES);
        
        double flagpoleLow = getLowest(flagpoleCandles);
        double flagpoleHigh = getHighest(flagpoleCandles);
        double flagpoleHeight = flagpoleHigh - flagpoleLow;
        
        int flagStart = flagpoleStart + MIN_FLAGPOLE_CANDLES;
        List<Candle> flagCandles = candles.subList(flagStart, candles.size());
        
        double flagHigh = getHighest(flagCandles);
        double flagLow = getLowest(flagCandles);
        
        Candle lastCandle = candles.get(candles.size() - 1);
        
        // Target is flag high + flagpole height
        double target = flagHigh + flagpoleHeight;
        double stopLoss = flagLow - (flagpoleHeight * 0.1);
        
        int confidence = calculateConfidence(candles, flagpoleHeight, flagHigh, flagLow, lastCandle);
        
        return PatternResult.builder()
            .pattern(ChartPattern.BULL_FLAG)
            .confidence(confidence)
            .breakoutPrice(lastCandle.getClose())
            .stopLoss(stopLoss)
            .target(target)
            .secondaryTarget(flagHigh)
            .description("Bull Flag: Flagpole=" + String.format("%.2f", flagpoleHeight) +
                ", Flag Range=" + String.format("%.2f-%.2f", flagLow, flagHigh))
            .patternLength(candles.size() - flagpoleStart)
            .direction("BULLISH")
            .riskRewardRatio(patternUtils.calculateRiskRewardRatio())
            .detectionTime(System.currentTimeMillis())
            .build();
    }
    
    private double getHighest(List<Candle> candles) {
        return candles.stream().mapToDouble(Candle::getHigh).max().orElse(0);
    }
    
    private double getLowest(List<Candle> candles) {
        return candles.stream().mapToDouble(Candle::getLow).min().orElse(Double.MAX_VALUE);
    }
    
    private int calculateConfidence(List<Candle> candles, double flagpoleHeight, double flagHigh, double flagLow, Candle lastCandle) {
        int confidence = 60;
        
        // Flagpole strength
        if (flagpoleHeight > 3.0) confidence += 15;
        else if (flagpoleHeight > 2.0) confidence += 10;
        
        // Volume
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() > avgVolume * 1.2) {
            confidence += 20;
        }
        
        // Breakout
        double breakout = Math.abs(patternUtils.percentageDifference(flagHigh, lastCandle.getClose()));
        if (breakout > 1.0) confidence += 15;
        
        return Math.min(100, confidence);
    }
}
