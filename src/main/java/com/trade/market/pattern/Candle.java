package com.trade.market.pattern;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Represents an OHLCV candlestick with time information.
 * Used for all chart pattern detection algorithms.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candle {
    
    /** Symbol identifier (e.g., AAPL, EUR/USD) */
    private String symbol;
    
    /** Opening price */
    private double open;
    
    /** Highest price during the period */
    private double high;
    
    /** Lowest price during the period */
    private double low;
    
    /** Closing price */
    private double close;
    
    /** Trading volume */
    private long volume;
    
    /** Start time of the candle (milliseconds since epoch) */
    private long startTime;
    
    /** End time of the candle (milliseconds since epoch) */
    private long endTime;
    
    /**
     * Gets the body of the candle (absolute difference between open and close).
     * 
     * @return absolute price movement from open to close
     */
    public double getBody() {
        return Math.abs(close - open);
    }
    
    /**
     * Gets the size of the upper shadow/wick.
     * 
     * @return distance from max(open, close) to high
     */
    public double getUpperWick() {
        return high - Math.max(open, close);
    }
    
    /**
     * Gets the size of the lower shadow/wick.
     * 
     * @return distance from min(open, close) to low
     */
    public double getLowerWick() {
        return Math.min(open, close) - low;
    }
    
    /**
     * Gets the total range of the candle.
     * 
     * @return difference between high and low
     */
    public double getRange() {
        return high - low;
    }
    
    /**
     * Checks if this is a bullish candle (close > open).
     * 
     * @return true if close > open, false otherwise
     */
    public boolean isBullish() {
        return close > open;
    }
    
    /**
     * Checks if this is a bearish candle (close < open).
     * 
     * @return true if close < open, false otherwise
     */
    public boolean isBearish() {
        return close < open;
    }
    
    /**
     * Checks if this is a doji candle (small body, significant wicks).
     * 
     * @return true if body is very small relative to range
     */
    public boolean isDoji() {
        double range = getRange();
        return range > 0 && getBody() / range < 0.1; // Body is less than 10% of range
    }
}
