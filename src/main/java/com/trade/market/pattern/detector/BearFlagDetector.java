package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects the Bear Flag chart pattern.
 * 
 * Pattern Characteristics:
 * - Strong flagpole: sharp, nearly vertical downtrend
 * - Flag body: consolidation with slight uptrend (retracement < 50%)
 * - Parallel channel forming the flag rectangle
 * - Breakdown below flag support on high volume
 * - Second downtrend similar to initial flagpole
 * 
 * Signal: Bearish continuation - strong downtrend continuation
 * Target: Flagpole height projected down from breakout
 * Stop Loss: Above flag resistance
 */
@Component
@Slf4j
public class BearFlagDetector implements PatternDetector {
    
    private final PivotDetector pivotDetector;
    private final PatternUtils patternUtils;
    
    // Configuration constants
    private static final int MIN_FLAGPOLE_CANDLES = 3;
    private static final int MIN_FLAG_CANDLES = 5;
    private static final double MAX_RETRACEMENT = 0.5;
    private static final double FLAGPOLE_MIN_DOWNTREND = 2.0;
    private static final double VOLUME_THRESHOLD = 0.8;
    private static final double BREAKOUT_THRESHOLD = 0.5;
    
    public BearFlagDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }
    
    @Override
    public ChartPattern pattern() {
        return ChartPattern.BEAR_FLAG;
    }
    
    @Override
    public String description() {
        return "Strong downtrend flagpole followed by rectangular consolidation (bear flag)";
    }
    
    @Override
    public int getPriority() {
        return 65;
    }
    
    
    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        if (candles == null || candles.size() < MIN_FLAGPOLE_CANDLES + MIN_FLAG_CANDLES) {
            return PatternResult.builder()
                .pattern(ChartPattern.BEAR_FLAG)
                .confidence(0)
                .description("Bear Flag: not enough candles to evaluate")
                .direction("BEARISH")
                .detectionTime(System.currentTimeMillis())
                .build();
        }

        int flagpoleStart = Math.max(0, candles.size() - MIN_FLAGPOLE_CANDLES - MIN_FLAG_CANDLES);
        List<Candle> flagpoleCandles = candles.subList(flagpoleStart, flagpoleStart + MIN_FLAGPOLE_CANDLES);
        
        double flagpoleHigh = getHighest(flagpoleCandles);
        double flagpoleLow = getLowest(flagpoleCandles);
        double flagpoleHeight = flagpoleHigh - flagpoleLow;

        // Verify the flagpole actually trended DOWN, not just that it had range.
        // A high-low spread alone doesn't tell you direction.
        double poleOpen = flagpoleCandles.get(0).getOpen();
        double poleClose = flagpoleCandles.get(flagpoleCandles.size() - 1).getClose();
        double poleDeclinePct = patternUtils.percentageDifference(poleOpen, poleClose);
        boolean isValidDowntrend = poleClose < poleOpen && Math.abs(poleDeclinePct) >= FLAGPOLE_MIN_DOWNTREND;

        if (!isValidDowntrend) {
            return PatternResult.builder()
                .pattern(ChartPattern.BEAR_FLAG)
                .confidence(0)
                .description("Bear Flag: flagpole segment did not show a sufficient downtrend")
                .direction("BEARISH")
                .detectionTime(System.currentTimeMillis())
                .build();
        }
        
        int flagStart = flagpoleStart + MIN_FLAGPOLE_CANDLES;
        List<Candle> flagCandles = candles.subList(flagStart, candles.size());
        
        double flagHigh = getHighest(flagCandles);
        double flagLow = getLowest(flagCandles);

        // Retracement: how much of the flagpole's decline has the flag body given back.
        // Bear flag expects this to stay under MAX_RETRACEMENT (50%).
        double retracement = flagpoleHeight > 0 ? (flagHigh - flagpoleLow) / flagpoleHeight : 1.0;
        
        Candle lastCandle = candles.get(candles.size() - 1);
        
        // Target is flag low - flagpole height
        double target = flagLow - flagpoleHeight;
        double stopLoss = flagHigh + (flagpoleHeight * 0.1);
        
        int confidence = calculateConfidence(candles, flagpoleHeight, flagHigh, flagLow, retracement, lastCandle);
        
        return PatternResult.builder()
            .pattern(ChartPattern.BEAR_FLAG)
            .confidence(confidence)
            .breakoutPrice(lastCandle.getClose())
            .stopLoss(stopLoss)
            .target(target)
            .secondaryTarget(flagLow)
            .description("Bear Flag: Flagpole=" + String.format("%.2f", flagpoleHeight) +
                ", Flag Range=" + String.format("%.2f-%.2f", flagLow, flagHigh))
            .patternLength(candles.size() - flagpoleStart)
            .direction("BEARISH")
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
    
    private int calculateConfidence(List<Candle> candles, double flagpoleHeight, double flagHigh, double flagLow,
                                     double retracement, Candle lastCandle) {
        int confidence = 60;
        
        // Flagpole strength
        if (flagpoleHeight > 3.0) confidence += 15;
        else if (flagpoleHeight > 2.0) confidence += 10;

        // Retracement discipline - now actually uses MAX_RETRACEMENT
        if (retracement <= MAX_RETRACEMENT) {
            confidence += 10;
        } else {
            // Flag body gave back too much of the flagpole's move - weaker setup
            confidence -= 15;
        }
        
        // Volume - now uses the declared threshold constant
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (lastCandle.getVolume() > avgVolume * (1 + VOLUME_THRESHOLD)) {
            confidence += 20;
        }
        
        // Breakdown - now uses the declared threshold constant
        double breakdown = Math.abs(patternUtils.percentageDifference(flagLow, lastCandle.getClose()));
        if (breakdown > BREAKOUT_THRESHOLD) confidence += 15;
        
        return Math.max(0, Math.min(100, confidence));
    }
}