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

    
 

    public void addCandle(String symbol, String timeframe, Candle candle) {
        addCandleByKey(symbol, timeframe, candle);
    }

    public void addCandleByKey(String seriesKey, String timeframe, Candle candle) {
        String storageKey = buildKey(seriesKey, timeframe);
        ReentrantLock lock = locks.computeIfAbsent(storageKey, k -> new ReentrantLock());

        lock.lock();
        try {
            BarSeries series = getOrCreateSeriesByKey(seriesKey, timeframe);

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
                        seriesKey, timeframe, newBar.getEndTime());
                return;
            }

            Bar lastBar = series.getLastBar();
            ZonedDateTime lastEndTime = lastBar.getEndTime();
            ZonedDateTime newEndTime = newBar.getEndTime();

            // New candle
            if (newEndTime.isAfter(lastEndTime)) {
                series.addBar(newBar);
                log.info("Added new candle [{}:{}] EndTime={}", seriesKey, timeframe, newEndTime);
                return;
            }
            // Duplicate candle
            if (newEndTime.isEqual(lastEndTime)) {
                log.debug("Duplicate candle ignored [{}:{}] EndTime={}", seriesKey, timeframe, newEndTime);
                return;
            }
            // Old candle
            log.debug("Old candle ignored [{}:{}] EndTime={}", seriesKey, timeframe, newEndTime);
        } catch (IllegalArgumentException ex) {
            // TA4J duplicate protection
            log.warn("TA4J rejected candle [{}:{}] : {}", seriesKey, timeframe, ex.getMessage());
        } catch (Exception ex) {
            log.error("Failed to add candle [{}:{}]", seriesKey, timeframe, ex);
        } finally {
            lock.unlock();
        }
    }

    public BarSeries getSeries(String symbol, String timeframe) {
        return getSeriesByKey(symbol, timeframe);
    }

    public BarSeries getSeriesByKey(String seriesKey, String timeframe) {
        return seriesByKey.get(buildKey(seriesKey, timeframe));
    }

    public BarSeries getOrCreateSeries(String symbol, String timeframe) {
        return getOrCreateSeriesByKey(symbol, timeframe);
    }

    public BarSeries getOrCreateSeriesByKey(String seriesKey, String timeframe) {
        String key = buildKey(seriesKey, timeframe);
        return seriesByKey.computeIfAbsent(key, ignored -> new BaseBarSeries(key));
    }

    private String buildKey(String symbol, String timeframe) {
        return symbol + ":" + timeframe;
    }
}
