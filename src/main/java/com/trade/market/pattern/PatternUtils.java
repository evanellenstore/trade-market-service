package com.trade.market.pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Utility class providing reusable calculations for pattern detection.
 * Includes trend analysis, trendline fitting, statistical calculations, and validations.
 */
@Component
@Slf4j
public class PatternUtils {
    
    // Constants
    private static final double TOLERANCE_THRESHOLD = 0.01; // 1% tolerance
    private static final double BREAKOUT_MIN_PERCENTAGE = 0.005; // 0.5% minimum
    private static final int MIN_VOLUME_SAMPLES = 5;
    
    /**
     * Checks if price A is higher than price B within a tolerance percentage.
     * 
     * @param priceA first price
     * @param priceB second price
     * @param tolerancePercent tolerance as percentage (e.g., 1.0 for 1%)
     * @return true if priceA > priceB within tolerance
     */
    public boolean isHigherWithTolerance(double priceA, double priceB, double tolerancePercent) {
        double tolerance = priceB * (tolerancePercent / 100.0);
        return priceA > (priceB - tolerance);
    }
    
    /**
     * Checks if price A is lower than price B within a tolerance percentage.
     * 
     * @param priceA first price
     * @param priceB second price
     * @param tolerancePercent tolerance as percentage
     * @return true if priceA < priceB within tolerance
     */
    public boolean isLowerWithTolerance(double priceA, double priceB, double tolerancePercent) {
        double tolerance = priceB * (tolerancePercent / 100.0);
        return priceA < (priceB + tolerance);
    }
    
    /**
     * Checks if two prices are approximately equal within tolerance.
     * 
     * @param priceA first price
     * @param priceB second price
     * @param tolerancePercent tolerance as percentage
     * @return true if prices are within tolerance
     */
    public boolean pricesApproximatelyEqual(double priceA, double priceB, double tolerancePercent) {
        double tolerance = Math.max(Math.abs(priceA), Math.abs(priceB)) * (tolerancePercent / 100.0);
        return Math.abs(priceA - priceB) <= tolerance;
    }
    
    /**
     * Calculates percentage difference between two prices.
     * 
     * @param fromPrice starting price (denominator)
     * @param toPrice ending price (numerator)
     * @return percentage change from fromPrice to toPrice
     */
    public double percentageDifference(double fromPrice, double toPrice) {
        if (fromPrice == 0) {
            return 0;
        }
        return ((toPrice - fromPrice) / fromPrice) * 100.0;
    }
    
    /**
     * Checks if a breakout has occurred (price movement with volume confirmation).
     * 
     * @param currentCandle the candle to check for breakout
     * @param resistancePrice the resistance level being broken
     * @param averageVolume typical volume for this symbol
     * @param minimumBreakoutPercent minimum breakout percentage required
     * @return true if breakout criteria are met
     */
    public boolean isBreakoutConfirmed(Candle currentCandle, double resistancePrice, 
                                      double averageVolume, double minimumBreakoutPercent) {
        // Check if close is above resistance
        if (currentCandle.getClose() <= resistancePrice) {
            return false;
        }
        
        // Check breakout percentage
        double breakoutPercent = percentageDifference(resistancePrice, currentCandle.getClose());
        if (breakoutPercent < minimumBreakoutPercent) {
            return false;
        }
        
        // Check volume confirmation (should be above average)
        if (currentCandle.getVolume() < averageVolume * 0.8) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Checks if a breakdown has occurred (price movement with volume confirmation).
     * 
     * @param currentCandle the candle to check for breakdown
     * @param supportPrice the support level being broken
     * @param averageVolume typical volume for this symbol
     * @param minimumBreakdownPercent minimum breakdown percentage required
     * @return true if breakdown criteria are met
     */
    public boolean isBreakdownConfirmed(Candle currentCandle, double supportPrice,
                                       double averageVolume, double minimumBreakdownPercent) {
        // Check if close is below support
        if (currentCandle.getClose() >= supportPrice) {
            return false;
        }
        
        // Check breakdown percentage
        double breakdownPercent = Math.abs(percentageDifference(supportPrice, currentCandle.getClose()));
        if (breakdownPercent < minimumBreakdownPercent) {
            return false;
        }
        
        // Check volume confirmation
        if (currentCandle.getVolume() < averageVolume * 0.8) {
            return false;
        }
        
        return true;
    }
    
    /**
     * Detects if candles form a higher high pattern (each high is higher than previous).
     * 
     * @param candles list of candles to analyze
     * @return true if all candles form higher highs
     */
    public boolean isHigherHighs(List<Candle> candles) {
        if (candles.size() < 2) {
            return false;
        }
        
        for (int i = 1; i < candles.size(); i++) {
            if (candles.get(i).getHigh() <= candles.get(i - 1).getHigh()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Detects if candles form a higher low pattern (each low is higher than previous).
     * 
     * @param candles list of candles to analyze
     * @return true if all candles form higher lows
     */
    public boolean isHigherLows(List<Candle> candles) {
        if (candles.size() < 2) {
            return false;
        }
        
        for (int i = 1; i < candles.size(); i++) {
            if (candles.get(i).getLow() <= candles.get(i - 1).getLow()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Detects if candles form a lower high pattern (each high is lower than previous).
     * 
     * @param candles list of candles to analyze
     * @return true if all candles form lower highs
     */
    public boolean isLowerHighs(List<Candle> candles) {
        if (candles.size() < 2) {
            return false;
        }
        
        for (int i = 1; i < candles.size(); i++) {
            if (candles.get(i).getHigh() >= candles.get(i - 1).getHigh()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Detects if candles form a lower low pattern (each low is lower than previous).
     * 
     * @param candles list of candles to analyze
     * @return true if all candles form lower lows
     */
    public boolean isLowerLows(List<Candle> candles) {
        if (candles.size() < 2) {
            return false;
        }
        
        for (int i = 1; i < candles.size(); i++) {
            if (candles.get(i).getLow() >= candles.get(i - 1).getLow()) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Calculates the slope between two prices over a given number of candles.
     * Positive slope indicates uptrend, negative indicates downtrend.
     * 
     * @param priceStart starting price
     * @param priceEnd ending price
     * @param candleCount number of candles between prices
     * @return slope (price change per candle)
     */
    public double calculateSlope(double priceStart, double priceEnd, int candleCount) {
        if (candleCount == 0) {
            return 0;
        }
        return (priceEnd - priceStart) / candleCount;
    }
    
    /**
     * Fits a linear trendline through a series of prices using simple linear regression.
     * Returns the slope of the trendline.
     * 
     * @param prices list of prices (usually highs or lows)
     * @return slope of the fitted trendline
     */
    public double calculateTrendlineSlope(List<Double> prices) {
        if (prices.size() < 2) {
            return 0;
        }
        
        int n = prices.size();
        double sumX = 0;
        double sumY = 0;
        double sumXY = 0;
        double sumX2 = 0;
        
        for (int i = 0; i < n; i++) {
            double x = i;
            double y = prices.get(i);
            sumX += x;
            sumY += y;
            sumXY += x * y;
            sumX2 += x * x;
        }
        
        double numerator = (n * sumXY) - (sumX * sumY);
        double denominator = (n * sumX2) - (sumX * sumX);
        
        if (denominator == 0) {
            return 0;
        }
        
        return numerator / denominator;
    }
    
    /**
     * Calculates the average volume over the last N candles.
     * 
     * @param candles list of candles to analyze
     * @param lookbackPeriod number of recent candles to include (0 = all)
     * @return average volume
     */
    public double calculateAverageVolume(List<Candle> candles, int lookbackPeriod) {
        if (candles.isEmpty()) {
            return 0;
        }
        
        int startIndex = lookbackPeriod > 0 ? 
            Math.max(0, candles.size() - lookbackPeriod) : 0;
        
        long totalVolume = 0;
        for (int i = startIndex; i < candles.size(); i++) {
            totalVolume += candles.get(i).getVolume();
        }
        
        int count = candles.size() - startIndex;
        return count > 0 ? (double) totalVolume / count : 0;
    }
    
    /**
     * Calculates the Average True Range (ATR) for volatility measurement.
     * ATR is the average of true ranges over a given period.
     * 
     * @param candles list of candles to analyze
     * @param period lookback period (typically 14)
     * @return ATR value
     */
    public double calculateATR(List<Candle> candles, int period) {
        if (candles.size() < period + 1) {
            return 0;
        }
        
        List<Double> trueRanges = new ArrayList<>();
        
        for (int i = 1; i < candles.size(); i++) {
            Candle current = candles.get(i);
            Candle previous = candles.get(i - 1);
            
            double tr = calculateTrueRange(current, previous);
            trueRanges.add(tr);
        }
        
        if (trueRanges.size() < period) {
            return 0;
        }
        
        // Calculate average of first 'period' true ranges
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += trueRanges.get(i);
        }
        
        return sum / period;
    }
    
    /**
     * Calculates the true range for a single candle.
     * True range = max(high - low, abs(high - prev close), abs(low - prev close))
     * 
     * @param current current candle
     * @param previous previous candle
     * @return true range value
     */
    public double calculateTrueRange(Candle current, Candle previous) {
        double range = current.getHigh() - current.getLow();
        double highLowPrevClose = Math.abs(current.getHigh() - previous.getClose());
        double lowPrevClose = Math.abs(current.getLow() - previous.getClose());
        
        return Math.max(range, Math.max(highLowPrevClose, lowPrevClose));
    }
    
    /**
     * Calculates the simple moving average of closes over a given period.
     * 
     * @param candles list of candles
     * @param period number of candles to average
     * @return simple moving average
     */
    public double calculateSMA(List<Candle> candles, int period) {
        if (candles.size() < period) {
            return 0;
        }
        
        double sum = 0;
        for (int i = candles.size() - period; i < candles.size(); i++) {
            sum += candles.get(i).getClose();
        }
        
        return sum / period;
    }
    
    /**
     * Calculates the exponential moving average of closes.
     * 
     * @param candles list of candles
     * @param period smoothing period
     * @return exponential moving average
     */
    public double calculateEMA(List<Candle> candles, int period) {
        if (candles.size() < period) {
            return 0;
        }
        
        // Start with SMA for the first EMA value
        double sma = calculateSMA(candles, period);
        double multiplier = 2.0 / (period + 1);
        double ema = sma;
        
        // Calculate EMA for remaining candles
        for (int i = candles.size() - period + 1; i < candles.size(); i++) {
            ema = (candles.get(i).getClose() * multiplier) + (ema * (1 - multiplier));
        }
        
        return ema;
    }
    
    /**
     * Calculates the distance between two pivot points as a percentage.
     * 
     * @param pivot1 first pivot
     * @param pivot2 second pivot
     * @return percentage distance between pivots
     */
    public double calculatePivotDistance(PivotPoint pivot1, PivotPoint pivot2) {
        return Math.abs(percentageDifference(pivot1.getPrice(), pivot2.getPrice()));
    }
    
    /**
     * Calculates the maximum drawdown in a series of prices.
     * Drawdown is the peak-to-trough decline during a given period.
     * 
     * @param candles list of candles to analyze
     * @return maximum drawdown as a percentage (0-100)
     */
    public double calculateMaxDrawdown(List<Candle> candles) {
        if (candles.isEmpty()) {
            return 0;
        }
        
        double maxDrawdown = 0;
        double peak = candles.get(0).getHigh();
        
        for (Candle candle : candles) {
            if (candle.getHigh() > peak) {
                peak = candle.getHigh();
            }
            double drawdown = percentageDifference(peak, candle.getLow());
            maxDrawdown = Math.min(drawdown, maxDrawdown);
        }
        
        return Math.abs(maxDrawdown);
    }
    
    /**
     * Detects if there is an uptrend using multiple confirmation methods.
     * Checks for higher highs, higher lows, and EMA alignment.
     * 
     * @param candles list of candles
     * @param lookbackPeriod number of candles to analyze
     * @return true if uptrend is detected
     */
    public boolean isUptrend(List<Candle> candles, int lookbackPeriod) {
        if (candles.size() < lookbackPeriod + 1) {
            return false;
        }
        
        int startIndex = candles.size() - lookbackPeriod;
        List<Candle> recentCandles = candles.subList(startIndex, candles.size());
        
        // Check for higher highs and higher lows
        if (!isHigherHighs(recentCandles) || !isHigherLows(recentCandles)) {
            return false;
        }
        
        // Additional check: close should be near or above the high range
        Candle lastCandle = candles.get(candles.size() - 1);
        double ema20 = calculateEMA(candles, 20);
        
        return lastCandle.getClose() > ema20;
    }
    
    /**
     * Detects if there is a downtrend using multiple confirmation methods.
     * Checks for lower highs, lower lows, and EMA alignment.
     * 
     * @param candles list of candles
     * @param lookbackPeriod number of candles to analyze
     * @return true if downtrend is detected
     */
    public boolean isDowntrend(List<Candle> candles, int lookbackPeriod) {
        if (candles.size() < lookbackPeriod + 1) {
            return false;
        }
        
        int startIndex = candles.size() - lookbackPeriod;
        List<Candle> recentCandles = candles.subList(startIndex, candles.size());
        
        // Check for lower highs and lower lows
        if (!isLowerHighs(recentCandles) || !isLowerLows(recentCandles)) {
            return false;
        }
        
        // Additional check: close should be near or below the low range
        Candle lastCandle = candles.get(candles.size() - 1);
        double ema20 = calculateEMA(candles, 20);
        
        return lastCandle.getClose() < ema20;
    }
    
    /**
     * Calculates the average distance between consecutive pivot highs.
     * 
     * @param pivots list of pivot points (should be highs)
     * @return average distance in price points
     */
    public double getAverageDistanceBetweenPivots(List<PivotPoint> pivots) {
        if (pivots.size() < 2) {
            return 0;
        }
        
        double totalDistance = 0;
        for (int i = 1; i < pivots.size(); i++) {
            totalDistance += pivots.get(i).distanceTo(pivots.get(i - 1));
        }
        
        return totalDistance / (pivots.size() - 1);
    }
    
    /**
     * Checks if a series of prices forms a converging pattern (like a triangle).
     * Convergence is when the distance between support and resistance lines is decreasing.
     * 
     * @param highs list of high prices
     * @param lows list of low prices
     * @return true if pattern is converging
     */
    public boolean isConvergingPattern(List<Double> highs, List<Double> lows) {
        if (highs.size() < 4 || lows.size() < 4) {
            return false;
        }
        
        // Calculate distance between resistance and support at beginning and end
        double initialSpread = highs.get(0) - lows.get(0);
        double finalSpread = highs.get(highs.size() - 1) - lows.get(lows.size() - 1);
        
        // Spread should be decreasing (convergence)
        return finalSpread < initialSpread;
    }
    
    /**
     * Gets the pivot with the highest price from a list of pivots.
     * 
     * @param pivots list of pivot points
     * @return the pivot with the highest price
     */
    public PivotPoint getHighestPivot(List<PivotPoint> pivots) {
        if (pivots == null || pivots.isEmpty()) {
            return null;
        }
        return pivots.stream().max((p1, p2) -> Double.compare(p1.getPrice(), p2.getPrice())).orElse(null);
    }
    
    /**
     * Gets the pivot with the lowest price from a list of pivots.
     * 
     * @param pivots list of pivot points
     * @return the pivot with the lowest price
     */
    public PivotPoint getLowestPivot(List<PivotPoint> pivots) {
        if (pivots == null || pivots.isEmpty()) {
            return null;
        }
        return pivots.stream().min((p1, p2) -> Double.compare(p1.getPrice(), p2.getPrice())).orElse(null);
    }
    
    /**
     * Calculates the risk-reward ratio for a trade.
     * For now, returns 1.0 as a placeholder (ratios calculated per detector).
     * 
     * @return risk-reward ratio
     */
    public double calculateRiskRewardRatio() {
        return 1.0;
    }
}
