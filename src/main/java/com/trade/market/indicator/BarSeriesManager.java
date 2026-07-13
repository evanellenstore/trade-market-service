package com.trade.market.indicator;

import com.trade.market.entity.Candle;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.num.DoubleNum;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class BarSeriesManager {

    private final Map<String, BarSeries> seriesByKey = new ConcurrentHashMap<>();

    public BarSeries getOrCreateSeries(String symbol, String timeframe) {
        String key = buildKey(symbol, timeframe);
        return seriesByKey.computeIfAbsent(key, ignored -> new BaseBarSeries(key));
    }

    public void addCandle(String symbol, String timeframe, Candle candle) {
        BarSeries series = getOrCreateSeries(symbol, timeframe);
        if (series.getBarCount() > 0) {
            BaseBar last = (BaseBar) series.getBar(series.getBarCount() - 1);
            ZonedDateTime candleEndTime = candle.getStartTime().atZone(ZoneId.of("UTC")).plus(Duration.ofMinutes(1));
            if (last.getEndTime().equals(candleEndTime)) {
                return;
            }
        }

        ZonedDateTime beginTime = candle.getStartTime().atZone(ZoneId.of("UTC"));
        BaseBar bar = new BaseBar(
                Duration.ofMinutes(1),
                beginTime,
                DoubleNum.valueOf(candle.getOpen()),
                DoubleNum.valueOf(candle.getHigh()),
                DoubleNum.valueOf(candle.getLow()),
                DoubleNum.valueOf(candle.getClose()),
                DoubleNum.valueOf(candle.getVolume()),
                DoubleNum.valueOf(candle.getVolume()),
                1L
        );
        series.addBar(bar);
        log.debug("Added candle to TA4J series {}:{}", symbol, timeframe);
    }

    public BarSeries getSeries(String symbol, String timeframe) {
        return seriesByKey.get(buildKey(symbol, timeframe));
    }

    private String buildKey(String symbol, String timeframe) {
        return symbol + ":" + timeframe;
    }
}
