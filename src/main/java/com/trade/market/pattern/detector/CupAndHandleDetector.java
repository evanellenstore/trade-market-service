package com.trade.market.pattern.detector;

import com.trade.market.pattern.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Detects the Cup and Handle chart pattern.
 *
 * Pattern Characteristics:
 * - Cup: rounded bottom formation (U-shaped)
 * - Handle: small pullback after cup (consolidation)
 * - Left rim and right rim at approximately equal heights
 * - Breakout above the rim level on increasing volume
 * - Cup depth 10-30% of previous uptrend
 * - Handle depth typically 5-10% of cup depth
 *
 * Signal: Bullish continuation - strong bullish bias
 * Target: Rim + cup depth
 * Stop Loss: Below handle low
 */
@Component
@Slf4j
public class CupAndHandleDetector implements PatternDetector {

    private final PivotDetector pivotDetector; // TODO: wire in for stronger rim/bottom pivot validation
    private final PatternUtils patternUtils;

    // Configuration constants
    private static final int MIN_CUP_CANDLES = 15;
    private static final int MIN_HANDLE_CANDLES = 3;
    private static final double CUP_DEPTH_MIN = 10.0;   // % of cup high
    private static final double CUP_DEPTH_MAX = 30.0;   // % of cup high
    private static final double RIM_TOLERANCE = 1.0;    // % allowed diff between left/right rim
    private static final double HANDLE_TOLERANCE = 2.0; // % slack around expected handle depth band
    private static final double HANDLE_DEPTH_MIN_PCT_OF_CUP = 5.0;
    private static final double HANDLE_DEPTH_MAX_PCT_OF_CUP = 10.0;
    private static final double VOLUME_THRESHOLD = 0.8; // handle volume should drop below this fraction of cup avg volume

    public CupAndHandleDetector(PivotDetector pivotDetector, PatternUtils patternUtils) {
        this.pivotDetector = pivotDetector;
        this.patternUtils = patternUtils;
    }

    @Override
    public ChartPattern pattern() {
        return ChartPattern.CUP_AND_HANDLE;
    }

    @Override
    public String description() {
        return "Rounded bottom (cup) with shallow pullback (handle), bullish continuation";
    }

    @Override
    public int getPriority() {
        return 60;
    }

    @Override
    public PatternResult detectWithResult(List<Candle> candles) {
        int minRequired = MIN_CUP_CANDLES + MIN_HANDLE_CANDLES;
        if (candles == null || candles.size() < minRequired) {
            log.debug("Not enough candles for cup and handle detection: need {}, got {}",
                minRequired, candles == null ? 0 : candles.size());
            return null;
        }

        // Handle is the tail segment; cup is everything before it.
        int handleStart = Math.max(MIN_CUP_CANDLES, candles.size() - MIN_HANDLE_CANDLES - 5);
        List<Candle> cupCandles = candles.subList(0, handleStart);
        List<Candle> handleCandles = candles.subList(handleStart, candles.size());

        if (cupCandles.isEmpty() || handleCandles.size() < MIN_HANDLE_CANDLES) {
            log.debug("Cup or handle segment too small after slicing");
            return null;
        }

        double cupHigh = cupCandles.stream().mapToDouble(Candle::getHigh).max().orElse(0);
        double cupLow = cupCandles.stream().mapToDouble(Candle::getLow).min().orElse(0);

        if (cupHigh <= 0 || cupLow <= 0 || cupLow >= cupHigh) {
            log.debug("Invalid cup high/low: high={}, low={}", cupHigh, cupLow);
            return null;
        }

        double cupDepth = cupHigh - cupLow;
        double cupDepthPct = cupDepth / cupHigh * 100;

        // Left rim = high of the first portion of the cup; right rim = high of the last portion before handle.
        int rimWindow = Math.max(1, cupCandles.size() / 5);
        double leftRim = cupCandles.subList(0, rimWindow).stream()
            .mapToDouble(Candle::getHigh).max().orElse(cupHigh);
        double rightRim = cupCandles.subList(cupCandles.size() - rimWindow, cupCandles.size()).stream()
            .mapToDouble(Candle::getHigh).max().orElse(cupHigh);
        double rimDiffPct = Math.abs(patternUtils.percentageDifference(leftRim, rightRim));

        double handleHigh = handleCandles.stream().mapToDouble(Candle::getHigh).max().orElse(0);
        double handleLow = handleCandles.stream().mapToDouble(Candle::getLow).min().orElse(0);
        double handleDepth = handleHigh - handleLow;
        double handleDepthPctOfCup = cupDepth > 0 ? (handleDepth / cupDepth * 100) : 0;

        Candle lastCandle = candles.get(candles.size() - 1);

        double target = cupHigh + cupDepth;
        double stopLoss = handleLow - (cupDepth * 0.1);

        boolean validCupDepth = cupDepthPct >= CUP_DEPTH_MIN && cupDepthPct <= CUP_DEPTH_MAX;
        boolean validRims = rimDiffPct <= RIM_TOLERANCE;
        boolean validHandleDepth =
            handleDepthPctOfCup >= (HANDLE_DEPTH_MIN_PCT_OF_CUP - HANDLE_TOLERANCE)
                && handleDepthPctOfCup <= (HANDLE_DEPTH_MAX_PCT_OF_CUP + HANDLE_TOLERANCE);
        boolean roundedBottom = isRoundedBottom(cupCandles, cupLow);

        int confidence = calculateConfidence(
            candles, cupCandles, handleCandles, cupDepthPct, lastCandle,
            validCupDepth, validRims, validHandleDepth, roundedBottom);

        return PatternResult.builder()
            .pattern(ChartPattern.CUP_AND_HANDLE)
            .confidence(confidence)
            .breakoutPrice(lastCandle.getClose())
            .stopLoss(stopLoss)
            .target(target)
            .secondaryTarget(cupHigh)
            .description("Cup and Handle: Rim=" + String.format("%.2f", cupHigh) +
                ", Bottom=" + String.format("%.2f", cupLow) +
                ", Depth=" + String.format("%.2f%%", cupDepthPct))
            .patternLength(candles.size())
            .direction("BULLISH")
            .riskRewardRatio(patternUtils.calculateRiskRewardRatio())
            .detectionTime(System.currentTimeMillis())
            .build();
    }

    private boolean isRoundedBottom(List<Candle> candles, double bottomPrice) {
        // Check if bottom has gradual approach (not sharp V)
        int bottomIndex = -1;
        for (int i = 0; i < candles.size(); i++) {
            if (candles.get(i).getLow() == bottomPrice) {
                bottomIndex = i;
                break;
            }
        }

        if (bottomIndex < 0 || bottomIndex > candles.size() - 2) {
            return false;
        }

        // Check for rounding (multiple candles near bottom)
        int nearBottomCount = 0;
        double tolerance = bottomPrice * 0.01; // 1% tolerance

        for (Candle candle : candles) {
            if (candle.getLow() <= bottomPrice + tolerance) {
                nearBottomCount++;
            }
        }

        return nearBottomCount >= 3; // At least 3 candles near the bottom
    }

    private int calculateConfidence(List<Candle> candles, List<Candle> cupCandles, List<Candle> handleCandles,
                                     double cupDepthPct, Candle lastCandle,
                                     boolean validCupDepth, boolean validRims,
                                     boolean validHandleDepth, boolean roundedBottom) {
        int confidence = 40;

        // Cup formation quality (percentage-based, consistent with CUP_DEPTH_MIN/MAX)
        if (validCupDepth) {
            confidence += 15;
        } else if (cupDepthPct > 0) {
            confidence += 5; // partial credit, wrong band
        }

        if (roundedBottom) {
            confidence += 10;
        }

        if (validRims) {
            confidence += 10;
        }

        if (validHandleDepth) {
            confidence += 10;
        }

        // Handle volume should contract relative to cup volume
        double cupAvgVolume = patternUtils.calculateAverageVolume(cupCandles, cupCandles.size());
        double handleAvgVolume = patternUtils.calculateAverageVolume(handleCandles, handleCandles.size());
        if (cupAvgVolume > 0 && handleAvgVolume <= cupAvgVolume * VOLUME_THRESHOLD) {
            confidence += 10;
        }

        // Breakout volume on the last candle vs recent average
        double avgVolume = patternUtils.calculateAverageVolume(candles, 20);
        if (avgVolume > 0 && lastCandle.getVolume() > avgVolume * 1.2) {
            confidence += 15;
        }

        // Breakout above rim
        double cupHigh = cupCandles.stream().mapToDouble(Candle::getHigh).max().orElse(0);
        double breakout = Math.abs(patternUtils.percentageDifference(cupHigh, lastCandle.getClose()));
        if (lastCandle.getClose() > cupHigh && breakout > 1.0) {
            confidence += 10;
        }

        return Math.min(100, confidence);
    }
}