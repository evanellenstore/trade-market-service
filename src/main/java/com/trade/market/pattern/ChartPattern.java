package com.trade.market.pattern;

/**
 * Enumeration of all supported chart patterns.
 */
public enum ChartPattern {
    
    /** Two peaks at approximately the same price level after an uptrend - bearish */
    DOUBLE_TOP("Double Top", "Bearish reversal pattern with two peaks"),
    
    /** Two troughs at approximately the same price level after a downtrend - bullish */
    DOUBLE_BOTTOM("Double Bottom", "Bullish reversal pattern with two valleys"),
    
    /** Three peaks with middle one highest - bearish reversal */
    HEAD_AND_SHOULDERS("Head and Shoulders", "Bearish reversal with three peaks"),
    
    /** Three troughs with middle one lowest - bullish reversal */
    INVERSE_HEAD_AND_SHOULDERS("Inverse Head and Shoulders", "Bullish reversal with three valleys"),
    
    /** Rising support line meets falling resistance line - bullish breakout */
    ASCENDING_TRIANGLE("Ascending Triangle", "Bullish continuation with rising support"),
    
    /** Rising resistance line meets falling support line - bearish breakdown */
    DESCENDING_TRIANGLE("Descending Triangle", "Bearish continuation with falling resistance"),
    
    /** Both support and resistance lines converge - breakout both directions */
    SYMMETRICAL_TRIANGLE("Symmetrical Triangle", "Consolidation pattern"),
    
    /** Strong uptrend followed by small retracement in parallel channel - bullish */
    BULL_FLAG("Bull Flag", "Bullish continuation after strong uptrend"),
    
    /** Strong downtrend followed by small retracement in parallel channel - bearish */
    BEAR_FLAG("Bear Flag", "Bearish continuation after strong downtrend"),
    
    /** Rising support and resistance converge with upside bias - bearish breakout */
    RISING_WEDGE("Rising Wedge", "Bearish pattern despite higher lows/highs"),
    
    /** Falling support and resistance converge with downside bias - bullish breakout */
    FALLING_WEDGE("Falling Wedge", "Bullish pattern despite lower lows/highs"),
    
    /** Rounded bottom (cup) with small pullback (handle) - bullish */
    CUP_AND_HANDLE("Cup and Handle", "Bullish continuation with rounded bottom"),
    
    /** Consolidation between two horizontal lines - breakout both directions */
    RECTANGLE("Rectangle", "Consolidation pattern with horizontal support/resistance"),
    
    /** Price breaks above resistance line after multiple touches - bullish */
    RESISTANCE_BREAKOUT("Resistance Breakout", "Bullish breakout above resistance"),
    
    /** Price breaks below support line after multiple touches - bearish */
    SUPPORT_BREAKDOWN("Support Breakdown", "Bearish breakdown below support"),
    
    /** No pattern detected */
    NONE("None", "No pattern detected");
    
    private final String displayName;
    private final String description;
    
    ChartPattern(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }
    
    public String getDisplayName() {
        return displayName;
    }
    
    public String getDescription() {
        return description;
    }
}
