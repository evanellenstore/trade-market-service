package com.trade.market.snapshot.builder;

import com.trade.market.dto.IndicatorResultDto;
import com.trade.market.entity.Candle;
import com.trade.market.pattern.PatternResult;
import com.trade.market.snapshot.dto.MarketSnapshot;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketSnapshotBuilderTest {
    private final MarketSnapshotBuilder builder = new MarketSnapshotBuilder();

    @Test
    void buildsDerivedFieldsAndCopiesMarketContext() {
        Candle previous = candle("VEDL-EQ", 100, 10);
        Candle current = candle("VEDL-EQ", 110, 25);
        current.setSymbolToken("3063");
        current.setExchange("NSE_CM");
        current.setTimeframe("ONE_MINUTE");
        IndicatorResultDto indicators = IndicatorResultDto.builder()
                .symbol("VEDL-EQ").symbolToken("3063").timeframe("ONE_MINUTE")
                .ema20(110).ema50(105).ema100(100).ema200(95)
                .rsi14(60).adx(45).macd(2).macdSignal(1).macdHistogram(1)
                .atr(2).vwap(109).supertrend(100).support1(98).resistance1(112)
                .build();

        MarketSnapshot snapshot = builder.build(current, indicators, PatternResult.none(), List.of(previous, current));

        assertEquals("BULLISH", snapshot.getTrend());
        assertEquals("STRONG", snapshot.getTrendStrength());
        assertEquals("TRENDING", snapshot.getMarketRegime());
        assertTrue(snapshot.isVolumeSpike());
        assertEquals("BUY", snapshot.getSupertrendSignal());
        assertEquals("NONE", snapshot.getPattern());
        assertEquals(110, snapshot.getPrice());
    }

    @Test
    void classifiesWeakSidewaysLowVolatilityWithoutEnoughVolumeHistory() {
        Candle current = candle("VEDL-EQ", 100, 10);
        IndicatorResultDto indicators = IndicatorResultDto.builder()
                .ema20(100).ema50(100).ema200(100).adx(20).atr(0.5).rsi14(50)
                .build();

        MarketSnapshot snapshot = builder.build(current, indicators, null, List.of(current));

        assertEquals("SIDEWAYS", snapshot.getTrend());
        assertEquals("WEAK", snapshot.getTrendStrength());
        assertEquals("LOW_VOLATILITY", snapshot.getMarketRegime());
        assertEquals("LOW", snapshot.getSignalStrength());
        assertEquals("UNKNOWN", snapshot.getSupertrendSignal());
        assertTrue(!snapshot.isVolumeSpike());
    }

    @Test
    void classifiesPriceEqualToSupertrendAsHold() {
        Candle current = candle("VEDL-EQ", 100, 10);
        IndicatorResultDto indicators = IndicatorResultDto.builder()
                .ema20(100).ema50(100).ema200(100).adx(20).atr(0.5).rsi14(50)
                .supertrend(100)
                .build();

        MarketSnapshot snapshot = builder.build(current, indicators, null, List.of(current));

        assertEquals("HOLD", snapshot.getSupertrendSignal());
    }

    private Candle candle(String symbol, double close, double volume) {
        return Candle.builder().symbol(symbol).close(close).volume(volume)
                .candleTime(LocalDateTime.of(2026, 8, 20, 15, 30)).build();
    }
}