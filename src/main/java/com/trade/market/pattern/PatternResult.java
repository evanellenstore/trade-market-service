package com.trade.market.pattern;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Represents the result of pattern detection with trading metrics.
 * Includes pattern identification, confidence score, and suggested price targets.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternResult {
    
    /** The detected chart pattern */
    private ChartPattern pattern;

    private boolean patternDetected;
    
    /** Confidence score from 0 to 100 (higher is more reliable) */
    private int confidence;
    
    /** Price level where breakout/breakdown occurred */
    private double breakoutPrice;
    
    /** Suggested stop loss price */
    private double stopLoss;
    
    /** Primary price target */
    private double target;
    
    /** Secondary price target (optional) */
    private Double secondaryTarget;
    
    /** Detailed description of the pattern */
    private String description;
    
    /** Number of candles that comprise the pattern */
    private int patternLength;
    
    /** Direction of the pattern: BULLISH, BEARISH, or NEUTRAL */
    private String direction;
    
    /** Risk-to-reward ratio (target - entry) / (entry - stop loss) */
    private double riskRewardRatio;
    
    /** Timestamp when pattern was detected (milliseconds since epoch) */
    private long detectionTime;
    
    /**
     * Creates a "no pattern" result.
     * 
     * @return PatternResult with NONE pattern and zero confidence
     */
    public static PatternResult none() {
        return PatternResult.builder()
            .pattern(ChartPattern.NONE)
            .patternDetected(false)
            .confidence(0)
            .description("NoPatternDetected")
            .build();
    }
    
    /**
     * Checks if a valid pattern was detected (not NONE).
     * 
     * @return true if a pattern other than NONE was detected
     */
    public boolean isPatternDetected() {
        return patternDetected;
    }
    
    /**
     * Checks if the pattern has high confidence (>= 70).
     * 
     * @return true if confidence is 70 or higher
     */
    public boolean isHighConfidence() {
        return confidence >= 70;
    }
    
    /**
     * Checks if the pattern has medium confidence (>= 50 and < 70).
     * 
     * @return true if confidence is between 50 and 70
     */
    public boolean isMediumConfidence() {
        return confidence >= 50 && confidence < 70;
    }
    
    /**
     * Calculates the potential profit from breakout price to target.
     * 
     * @return profit in absolute price points
     */
    public double calculateProfit() {
        if (breakoutPrice == 0) {
            return 0;
        }
        return Math.abs(target - breakoutPrice);
    }
    
    /**
     * Calculates the potential loss from breakout price to stop loss.
     * 
     * @return loss in absolute price points
     */
    public double calculateLoss() {
        if (breakoutPrice == 0) {
            return 0;
        }
        return Math.abs(breakoutPrice - stopLoss);
    }
    
    /**
     * Calculates risk-to-reward ratio.
     * 
     * @return ratio of profit to loss, or 0 if loss is zero
     */
    public double calculateRiskRewardRatio() {
        double loss = calculateLoss();
        if (loss == 0) {
            return 0;
        }
        return calculateProfit() / loss;
    }
}
