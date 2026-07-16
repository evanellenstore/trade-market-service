package com.trade.market.pattern;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Represents a pivot point (swing high or swing low) in price action.
 * Pivot points are used as the foundation for all pattern detection algorithms.
 */
@Getter
@Builder
@AllArgsConstructor
public class PivotPoint {
    
    /** Enumeration of pivot types */
    public enum PivotType {
        HIGH("Swing High"),
        LOW("Swing Low");
        
        private final String label;
        
        PivotType(String label) {
            this.label = label;
        }
        
        public String getLabel() {
            return label;
        }
    }
    
    /** Type of pivot (HIGH or LOW) */
    private PivotType type;
    
    /** Price level of the pivot */
    private double price;
    
    /** Index of the candle where this pivot occurs */
    private int index;
    
    /** Volume at pivot (for additional validation) */
    private long volume;
    
    /** Time of pivot in milliseconds since epoch */
    private long time;
    
    /**
     * Check if this pivot is higher than another pivot.
     * 
     * @param other the pivot to compare to
     * @return true if this pivot's price is higher
     */
    public boolean isHigherThan(PivotPoint other) {
        return this.price > other.price;
    }
    
    /**
     * Check if this pivot is lower than another pivot.
     * 
     * @param other the pivot to compare to
     * @return true if this pivot's price is lower
     */
    public boolean isLowerThan(PivotPoint other) {
        return this.price < other.price;
    }
    
    /**
     * Calculate the distance between this pivot and another.
     * 
     * @param other the pivot to measure distance to
     * @return absolute price difference
     */
    public double distanceTo(PivotPoint other) {
        return Math.abs(this.price - other.price);
    }
    
    /**
     * Calculate percentage difference between this pivot and another.
     * 
     * @param other the pivot to compare to
     * @return percentage difference relative to this pivot's price
     */
    public double percentDifference(PivotPoint other) {
        if (this.price == 0) {
            return 0;
        }
        return ((other.price - this.price) / this.price) * 100.0;
    }
}
