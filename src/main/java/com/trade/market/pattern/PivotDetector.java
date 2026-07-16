package com.trade.market.pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Detects swing highs and swing lows (pivot points) in OHLC candle data.
 * Pivot points are the foundation for all chart pattern detection.
 * 
 * A swing high is a candle where the high is greater than the highs of
 * the candles to its left and right (lookback and lookahead periods).
 * 
 * A swing low is the opposite - a candle where the low is less than
 * the lows of the candles to its left and right.
 */
@Component
@Slf4j
public class PivotDetector {
    
    // Configuration constants
    private static final int DEFAULT_LEFT_BARS = 3;
    private static final int DEFAULT_RIGHT_BARS = 3;
    
    /**
     * Detects all pivot points (swing highs and lows) in the given candles.
     * Uses default lookback and lookahead periods.
     * 
     * @param candles list of candles to analyze
     * @return list of detected pivot points
     */
    public List<PivotPoint> detectPivots(List<Candle> candles) {
        return detectPivots(candles, DEFAULT_LEFT_BARS, DEFAULT_RIGHT_BARS);
    }
    
    /**
     * Detects pivot points with configurable lookback and lookahead periods.
     * 
     * @param candles list of candles to analyze
     * @param leftBars number of candles to look back (before the pivot)
     * @param rightBars number of candles to look ahead (after the pivot)
     * @return list of detected pivot points, ordered chronologically
     */
    public List<PivotPoint> detectPivots(List<Candle> candles, int leftBars, int rightBars) {
        List<PivotPoint> pivots = new ArrayList<>();
        
        if (candles.size() < leftBars + rightBars + 1) {
            log.warn("Insufficient candles for pivot detection: {} (need {})", 
                candles.size(), leftBars + rightBars + 1);
            return pivots;
        }
        
        // Iterate through candles, starting after leftBars and stopping before rightBars from end
        for (int i = leftBars; i < candles.size() - rightBars; i++) {
            Candle current = candles.get(i);
            
            // Check for swing high
            if (isSwingHigh(candles, i, leftBars, rightBars)) {
                PivotPoint pivot = PivotPoint.builder()
                    .type(PivotPoint.PivotType.HIGH)
                    .price(current.getHigh())
                    .index(i)
                    .volume(current.getVolume())
                    .time(current.getEndTime())
                    .build();
                pivots.add(pivot);
            }
            
            // Check for swing low
            if (isSwingLow(candles, i, leftBars, rightBars)) {
                PivotPoint pivot = PivotPoint.builder()
                    .type(PivotPoint.PivotType.LOW)
                    .price(current.getLow())
                    .index(i)
                    .volume(current.getVolume())
                    .time(current.getEndTime())
                    .build();
                pivots.add(pivot);
            }
        }
        
        return pivots;
    }
    
    /**
     * Detects only swing highs (resistance points).
     * 
     * @param candles list of candles
     * @param leftBars lookback period
     * @param rightBars lookahead period
     * @return list of swing high pivots
     */
    public List<PivotPoint> detectSwingHighs(List<Candle> candles, int leftBars, int rightBars) {
        List<PivotPoint> highs = new ArrayList<>();
        
        if (candles.size() < leftBars + rightBars + 1) {
            return highs;
        }
        
        for (int i = leftBars; i < candles.size() - rightBars; i++) {
            if (isSwingHigh(candles, i, leftBars, rightBars)) {
                Candle current = candles.get(i);
                PivotPoint pivot = PivotPoint.builder()
                    .type(PivotPoint.PivotType.HIGH)
                    .price(current.getHigh())
                    .index(i)
                    .volume(current.getVolume())
                    .time(current.getEndTime())
                    .build();
                highs.add(pivot);
            }
        }
        
        return highs;
    }
    
    /**
     * Detects only swing lows (support points).
     * 
     * @param candles list of candles
     * @param leftBars lookback period
     * @param rightBars lookahead period
     * @return list of swing low pivots
     */
    public List<PivotPoint> detectSwingLows(List<Candle> candles, int leftBars, int rightBars) {
        List<PivotPoint> lows = new ArrayList<>();
        
        if (candles.size() < leftBars + rightBars + 1) {
            return lows;
        }
        
        for (int i = leftBars; i < candles.size() - rightBars; i++) {
            if (isSwingLow(candles, i, leftBars, rightBars)) {
                Candle current = candles.get(i);
                PivotPoint pivot = PivotPoint.builder()
                    .type(PivotPoint.PivotType.LOW)
                    .price(current.getLow())
                    .index(i)
                    .volume(current.getVolume())
                    .time(current.getEndTime())
                    .build();
                lows.add(pivot);
            }
        }
        
        return lows;
    }
    
    /**
     * Gets the most recent pivot high from a list of candles.
     * 
     * @param candles list of candles
     * @param leftBars lookback period
     * @param rightBars lookahead period
     * @return the most recent swing high, or null if none exists
     */
    public PivotPoint getLastSwingHigh(List<Candle> candles, int leftBars, int rightBars) {
        List<PivotPoint> highs = detectSwingHighs(candles, leftBars, rightBars);
        return highs.isEmpty() ? null : highs.get(highs.size() - 1);
    }
    
    /**
     * Gets the most recent pivot low from a list of candles.
     * 
     * @param candles list of candles
     * @param leftBars lookback period
     * @param rightBars lookahead period
     * @return the most recent swing low, or null if none exists
     */
    public PivotPoint getLastSwingLow(List<Candle> candles, int leftBars, int rightBars) {
        List<PivotPoint> lows = detectSwingLows(candles, leftBars, rightBars);
        return lows.isEmpty() ? null : lows.get(lows.size() - 1);
    }
    
    /**
     * Gets the highest pivot point from a list of pivots.
     * 
     * @param pivots list of pivot points
     * @return the pivot with the highest price, or null if list is empty
     */
    public PivotPoint getHighestPivot(List<PivotPoint> pivots) {
        if (pivots.isEmpty()) {
            return null;
        }
        
        PivotPoint highest = pivots.get(0);
        for (PivotPoint pivot : pivots) {
            if (pivot.getPrice() > highest.getPrice()) {
                highest = pivot;
            }
        }
        return highest;
    }
    
    /**
     * Gets the lowest pivot point from a list of pivots.
     * 
     * @param pivots list of pivot points
     * @return the pivot with the lowest price, or null if list is empty
     */
    public PivotPoint getLowestPivot(List<PivotPoint> pivots) {
        if (pivots.isEmpty()) {
            return null;
        }
        
        PivotPoint lowest = pivots.get(0);
        for (PivotPoint pivot : pivots) {
            if (pivot.getPrice() < lowest.getPrice()) {
                lowest = pivot;
            }
        }
        return lowest;
    }
    
    // ==================== Private Helper Methods ====================
    
    /**
     * Checks if the candle at the given index is a swing high.
     * A swing high is higher than all candles within the lookback/lookahead windows.
     * 
     * @param candles list of candles
     * @param centerIndex index of the candle to check
     * @param leftBars lookback period
     * @param rightBars lookahead period
     * @return true if centerIndex candle is a swing high
     */
    private boolean isSwingHigh(List<Candle> candles, int centerIndex, int leftBars, int rightBars) {
        Candle center = candles.get(centerIndex);
        double centerHigh = center.getHigh();
        
        // Check all candles within leftBars period
        for (int i = centerIndex - leftBars; i < centerIndex; i++) {
            if (candles.get(i).getHigh() >= centerHigh) {
                return false;
            }
        }
        
        // Check all candles within rightBars period
        for (int i = centerIndex + 1; i <= centerIndex + rightBars; i++) {
            if (candles.get(i).getHigh() >= centerHigh) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Checks if the candle at the given index is a swing low.
     * A swing low is lower than all candles within the lookback/lookahead windows.
     * 
     * @param candles list of candles
     * @param centerIndex index of the candle to check
     * @param leftBars lookback period
     * @param rightBars lookahead period
     * @return true if centerIndex candle is a swing low
     */
    private boolean isSwingLow(List<Candle> candles, int centerIndex, int leftBars, int rightBars) {
        Candle center = candles.get(centerIndex);
        double centerLow = center.getLow();
        
        // Check all candles within leftBars period
        for (int i = centerIndex - leftBars; i < centerIndex; i++) {
            if (candles.get(i).getLow() <= centerLow) {
                return false;
            }
        }
        
        // Check all candles within rightBars period
        for (int i = centerIndex + 1; i <= centerIndex + rightBars; i++) {
            if (candles.get(i).getLow() <= centerLow) {
                return false;
            }
        }
        
        return true;
    }
}
