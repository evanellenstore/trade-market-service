package com.trade.market.pattern;

import java.util.List;

/**
 * Interface that all chart pattern detectors must implement.
 * Each detector is responsible for identifying one specific chart pattern.
 */
public interface PatternDetector {
    
    /**
     * Detects if the pattern exists in the given candlestick data.
     * 
     * @param candles list of candlesticks to analyze (must not be empty)
     * @return true if the pattern is detected, false otherwise
     */
    boolean detect(List<Candle> candles);
    
    /**
     * Returns the chart pattern that this detector identifies.
     * 
     * @return the ChartPattern enum value for this detector
     */
    ChartPattern pattern();
    
    /**
     * Provides a human-readable description of the detection logic.
     * 
     * @return description of the pattern and detection criteria
     */
    String description();
    
    /**
     * Returns a detailed PatternResult if the pattern is detected.
     * This method is called after successful detect() to get full details.
     * 
     * @param candles list of candlesticks being analyzed
     * @return PatternResult with confidence, targets, and stop loss, or null if not detected
     */
    PatternResult detectWithResult(List<Candle> candles);
    
    /**
     * Gets the priority/order of detection for this detector.
     * Detectors with higher priority are checked first.
     * Default is 50 (neutral priority).
     * 
     * @return priority value (higher = checked first)
     */
    default int getPriority() {
        return 50;
    }
}
