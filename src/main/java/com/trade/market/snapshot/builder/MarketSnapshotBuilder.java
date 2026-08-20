package com.trade.market.snapshot.builder;

import com.trade.market.dto.IndicatorResultDto;
import com.trade.market.entity.Candle;
import com.trade.market.pattern.ChartPattern;
import com.trade.market.pattern.PatternResult;
import com.trade.market.snapshot.dto.MarketSnapshot;
import com.trade.market.snapshot.util.MarketRegimeCalculator;
import com.trade.market.snapshot.util.SignalStrengthCalculator;
import com.trade.market.snapshot.util.TrendCalculator;
import com.trade.market.snapshot.util.VolumeSpikeCalculator;

import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/** Builds an immutable-in-use snapshot from the market enrichment results. */
@Component
public class MarketSnapshotBuilder {
    public MarketSnapshot build(Candle candle, IndicatorResultDto indicators,
                                 PatternResult pattern, List<Candle> orderedCandles) {
        Objects.requireNonNull(candle, "candle must not be null");
        Objects.requireNonNull(indicators, "indicators must not be null");
        String trend = TrendCalculator.calculate(indicators);
        PatternResult effectivePattern = pattern == null ? PatternResult.none() : pattern;
        String patternName = effectivePattern.isPatternDetected() && effectivePattern.getPattern() != null
                ? effectivePattern.getPattern().name() : ChartPattern.NONE.name();

        return MarketSnapshot.builder()
                .symbol(candle.getSymbol())
                .symbolToken(candle.getSymbolToken())
                .exchange(candle.getExchange())
                .timeframe(candle.getTimeframe())
                .price(candle.getClose())
                .trend(trend)
                .trendStrength(calculateTrendStrength(indicators.getAdx()))
                .marketRegime(MarketRegimeCalculator.calculate(indicators.getAdx(), indicators.getAtr(), candle.getClose()))
                .volumeSpike(VolumeSpikeCalculator.calculate(orderedCandles))
                .rsi14(indicators.getRsi14())
                .adx(indicators.getAdx())
                .ema20(indicators.getEma20())
                .ema50(indicators.getEma50())
                .ema100(indicators.getEma100())
                .ema200(indicators.getEma200())
                .macd(indicators.getMacd())
                .macdSignal(indicators.getMacdSignal())
                .macdHistogram(indicators.getMacdHistogram())
                .atr(indicators.getAtr())
                .vwap(indicators.getVwap())
                .supertrendSignal(calculateSupertrendSignal(candle.getClose(), indicators.getSupertrend()))
                .pattern(patternName)
                .support1(indicators.getSupport1())
                .resistance1(indicators.getResistance1())
                .signalStrength(SignalStrengthCalculator.calculate(trend, indicators))
                .snapshotTime(candle.getCandleTime())
                .runId(indicators.getRunId())
                .origin(indicators.getOrigin())
                .build();
    }

    private String calculateTrendStrength(double adx) {
        if (adx > 40) return "STRONG";
        if (adx >= 25) return "MEDIUM";
        return "WEAK";
    }

    private String calculateSupertrendSignal(double price, double supertrend) {
        if (supertrend == 0) return "UNKNOWN";
        return price >= supertrend ? "BUY" : "SELL";
    }
}