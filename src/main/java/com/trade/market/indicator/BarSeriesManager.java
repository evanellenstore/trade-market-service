package com.trade.market.indicator;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

import org.springframework.stereotype.Component;
import org.ta4j.core.Bar;
import org.ta4j.core.BarSeries;
import org.ta4j.core.BaseBar;
import org.ta4j.core.BaseBarSeries;
import org.ta4j.core.num.DecimalNum;

import com.trade.market.entity.Candle;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class BarSeriesManager {

    private final Map<String, BarSeries> seriesByKey = new ConcurrentHashMap<>();
    private final Map<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    public BarSeries getOrCreateSeries(String symbol, String timeframe) {
        String key = buildKey(symbol, timeframe);
        return seriesByKey.computeIfAbsent(key, ignored -> new BaseBarSeries(key));
    }

    /* 
    public void addCandle(String symbol, String timeframe, Candle candle) {
        String key = buildKey(symbol, timeframe);
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());
        lock.lock();
        try {
            BarSeries series = getOrCreateSeries(symbol, timeframe);
            if (series.getBarCount() > 0) {
                BaseBar last = (BaseBar) series.getBar(series.getBarCount() - 1);
                ZonedDateTime candleEndTime = candle.getStartTime().atZone(ZoneId.of("UTC")).plus(Duration.ofMinutes(1));
                // skip if candle end is not strictly after the series last end (prevents <= error)
                if (!candleEndTime.isAfter(last.getEndTime())) {
                    log.debug("Skipping candle with endTime {} because series endTime is {}", candleEndTime, last.getEndTime());
                    return;
                }
            }

            ZonedDateTime beginTime = candle.getStartTime().atZone(ZoneId.of("UTC"));
            BaseBar bar = new BaseBar(
                    Duration.ofMinutes(1),
                    beginTime,
                DecimalNum.valueOf(candle.getOpen()),
                DecimalNum.valueOf(candle.getHigh()),
                DecimalNum.valueOf(candle.getLow()),
                DecimalNum.valueOf(candle.getClose()),
                DecimalNum.valueOf(candle.getVolume()),
                DecimalNum.valueOf(candle.getVolume()),
                    1L
            );
            try {
                series.addBar(bar);
                log.debug("Added candle to TA4J series {}:{}", symbol, timeframe);
            } catch (IllegalArgumentException e) {
                // TA4J will throw when attempting to add a bar with endTime <= series endTime.
                // This can happen under races or when the scheduled job attempts to re-add the newest candle.
                log.debug("Ignored TA4J addBar error for {}:{} end={} ",
                    symbol, timeframe, bar.getEndTime(), e);
            }
        } finally {
            lock.unlock();
        }
    }*/

 

    public void addCandle(String symbol, String timeframe, Candle candle) {

        String key = buildKey(symbol, timeframe);
        ReentrantLock lock = locks.computeIfAbsent(key, k -> new ReentrantLock());

        lock.lock();
        try {

            BarSeries series = getOrCreateSeries(symbol, timeframe);

            ZonedDateTime beginTime = candle.getStartTime()
                    .atZone(ZoneId.of("UTC"));

            BaseBar newBar = new BaseBar(
                    Duration.ofMinutes(1),
                    beginTime,
                    DecimalNum.valueOf(candle.getOpen()),
                    DecimalNum.valueOf(candle.getHigh()),
                    DecimalNum.valueOf(candle.getLow()),
                    DecimalNum.valueOf(candle.getClose()),
                    DecimalNum.valueOf(candle.getVolume()),
                    DecimalNum.valueOf(candle.getVolume()),
                    1L);

            // First bar
            if (series.isEmpty()) {
                series.addBar(newBar);
                log.info("Added first candle [{}:{}] EndTime={}",
                        symbol, timeframe, newBar.getEndTime());
                return;
            }

            Bar lastBar = series.getLastBar();
            ZonedDateTime lastEndTime = lastBar.getEndTime();
            ZonedDateTime newEndTime = newBar.getEndTime();

            // New candle
            if (newEndTime.isAfter(lastEndTime)) {
                series.addBar(newBar);
                log.info("Added new candle [{}:{}] EndTime={}", symbol, timeframe, newEndTime);
                return;
            }
            // Duplicate candle
            if (newEndTime.isEqual(lastEndTime)) {
                log.debug("Duplicate candle ignored [{}:{}] EndTime={}", symbol, timeframe, newEndTime);
                return;
            }
            // Old candle
            log.debug("Old candle ignored [{}:{}] EndTime={}", symbol, timeframe, newEndTime);
        } catch (IllegalArgumentException ex) {
            // TA4J duplicate protection
            log.warn("TA4J rejected candle [{}:{}] : {}", symbol, timeframe, ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to add candle [{}:{}]", symbol, timeframe, ex);
        } finally {
            lock.unlock();
        }
    }

    public BarSeries getSeries(String symbol, String timeframe) {
        return seriesByKey.get(buildKey(symbol, timeframe));
    }

    private String buildKey(String symbol, String timeframe) {
        return symbol + ":" + timeframe;
    }
}
