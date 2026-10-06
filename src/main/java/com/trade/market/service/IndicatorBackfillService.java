package com.trade.market.service;

import com.trade.market.dto.IndicatorBackfillRequest;
import com.trade.market.dto.IndicatorBackfillStatusDto;
import com.trade.market.dto.IndicatorResultDto;
import com.trade.market.entity.BacktestCandle;
import com.trade.market.entity.BacktestMarketIndicator;
import com.trade.market.entity.Candle;
import com.trade.market.indicator.BarSeriesManager;
import com.trade.market.repository.BacktestCandleRepository;
import com.trade.market.repository.BacktestMarketIndicatorRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndicatorBackfillService {
    private static final int BATCH_SIZE = 500;
    private static final int SERIES_MAX_BAR_COUNT = 1000;

    private final BacktestCandleRepository backtestCandleRepository;
    private final BacktestMarketIndicatorRepository backtestIndicatorRepository;
    private final IndicatorService indicatorService;
    private final BarSeriesManager barSeriesManager;
    private final JdbcTemplate jdbcTemplate;

    public String start(IndicatorBackfillRequest request) {
        String source = request.getSource() == null ? "BACKTEST" : request.getSource().trim().toUpperCase();
        if (!"BACKTEST".equals(source)) {
            throw new IllegalArgumentException("Only BACKTEST indicator backfill is supported");
        }
        String runId = "indicator-backfill-" + UUID.randomUUID();
        backfillAsync(request.getSymbolTokens(), request.getTimeframe(), runId);
        return runId;
    }

    public String startAll() {
        String runId = "indicator-backfill-" + UUID.randomUUID();
        backfillAllAsync(runId);
        return runId;
    }

    public List<IndicatorBackfillStatusDto> getStatus(List<String> symbolTokens, String timeframe) {
        if (symbolTokens == null || symbolTokens.isEmpty() || timeframe == null || timeframe.isBlank()) {
            return List.of();
        }
        return backtestIndicatorRepository.findBackfillStatus(symbolTokens, timeframe);
    }

    @Async
    public void backfillAllAsync(String runId) {
        for (String symbolToken : backtestCandleRepository.findDistinctSymbolTokens()) {
            for (String timeframe : backtestCandleRepository.findDistinctTimeframesBySymbolToken(symbolToken)) {
                backfill(symbolToken, timeframe, runId);
            }
        }
    }

    @Async
    public void backfillAsync(List<String> symbolTokens, String timeframe, String runId) {
        for (String symbolToken : symbolTokens) {
            backfill(symbolToken, timeframe, runId);
        }
    }

    private void backfill(String symbolToken, String timeframe, String runId) {

        System.out.println("-----findBySymbolTokenAndTimeframeOrderByCandleTimeAsc-----");
        List<BacktestCandle> candles = backtestCandleRepository
                .findBySymbolTokenAndTimeframeOrderByCandleTimeAsc(symbolToken, timeframe);
        if (candles.isEmpty()) {
            log.info("No backtest candles found for symbolToken={} timeframe={}", symbolToken, timeframe);
            return;
        }
        System.out.println("=========== Number of candles found: " + candles.size());

        String symbol = candles.get(0).getSymbol();
        String seriesKey = symbolToken + "::" + timeframe + "::" + runId;
        List<Double> closes = new ArrayList<>();
        List<BacktestMarketIndicator> indicatorBatch = new ArrayList<>(BATCH_SIZE);
        long batchStartedAt = System.nanoTime();

       
        for (BacktestCandle backtestCandle : candles) {
            closes.add(backtestCandle.getClose());
            if (closes.size() > SERIES_MAX_BAR_COUNT) {
                closes.remove(0);
            }
            Candle candle = toMarketCandle(backtestCandle);
            barSeriesManager.addCandleByKey(seriesKey, timeframe, candle, SERIES_MAX_BAR_COUNT);
            IndicatorResultDto result = indicatorService.calculateIndicatorsBySeriesKey(symbol, timeframe, seriesKey, candle.getSymbolToken(), candle.getCandleTime(), closes);
            indicatorBatch.add(toBacktestIndicator(result, runId, backtestCandle));
            
            
            if (indicatorBatch.size() == BATCH_SIZE) {
                persistIndicatorBatch(indicatorBatch, symbolToken, timeframe, batchStartedAt);
                indicatorBatch.clear();
                batchStartedAt = System.nanoTime();
            }
        }
        if (!indicatorBatch.isEmpty()) {
            persistIndicatorBatch(indicatorBatch, symbolToken, timeframe, batchStartedAt);
        }
        log.info("Completed indicator backfill: symbol={} timeframe={} candles={} runId={}",
                symbol, timeframe, candles.size(), runId);
    }

    private void persistIndicatorBatch(List<BacktestMarketIndicator> indicators, String symbolToken,
                                       String timeframe, long batchStartedAt) {
        String candleIdPlaceholders = String.join(",", Collections.nCopies(indicators.size(), "?"));
        String findSql = "SELECT id, candle_id, candle_time FROM market_indicators_backtest WHERE "
                + "candle_id IN (" + candleIdPlaceholders + ") OR "
                + "(symbol_token = ? AND timeframe = ? AND candle_time >= ? AND candle_time <= ?)";
        List<Object> queryParameters = new ArrayList<>(indicators.size() + 4);
        Map<Long, BacktestMarketIndicator> indicatorsByCandleId = new HashMap<>();
        for (BacktestMarketIndicator indicator : indicators) {
            Long candleId = indicator.getCandle().getId();
            queryParameters.add(candleId);
            indicatorsByCandleId.put(candleId, indicator);
        }
        queryParameters.add(symbolToken);
        queryParameters.add(timeframe);
        queryParameters.add(indicators.get(0).getCandleTime());
        queryParameters.add(indicators.get(indicators.size() - 1).getCandleTime());

        Map<Long, ExistingIndicator> existingByCandleId = new HashMap<>();
        Map<java.time.LocalDateTime, ExistingIndicator> existingByTime = new HashMap<>();
        jdbcTemplate.query(findSql, resultSet -> {
            java.time.LocalDateTime candleTime = resultSet.getTimestamp("candle_time").toLocalDateTime();
            Long candleId = resultSet.getObject("candle_id", Long.class);
            ExistingIndicator existing = new ExistingIndicator(resultSet.getLong("id"), candleId);
            if (candleId != null && indicatorsByCandleId.containsKey(candleId)) {
                existingByCandleId.put(candleId, existing);
            }
            existingByTime.put(candleTime, existing);
        }, queryParameters.toArray());

        List<Object[]> rowsToInsert = new ArrayList<>();
        List<Object[]> candleLinksToUpdate = new ArrayList<>();
        for (BacktestMarketIndicator indicator : indicators) {
            ExistingIndicator existing = existingByCandleId.get(indicator.getCandle().getId());
            if (existing == null) {
                existing = existingByTime.get(indicator.getCandleTime());
            }
            if (existing == null) {
                rowsToInsert.add(toInsertParameters(indicator));
            } else if (existing.candleId() == null) {
                candleLinksToUpdate.add(new Object[]{indicator.getCandle().getId(), existing.id()});
            }
        }

        if (!candleLinksToUpdate.isEmpty()) {
            jdbcTemplate.batchUpdate("UPDATE market_indicators_backtest SET candle_id = ? "
                    + "WHERE id = ? AND candle_id IS NULL", candleLinksToUpdate);
        }
        if (!rowsToInsert.isEmpty()) {
            jdbcTemplate.batchUpdate("INSERT INTO market_indicators_backtest "
                    + "(candle_id, symbol, symbol_token, timeframe, run_id, origin, candle_time, "
                    + "trend_ema, trend_ema20, trend_ema50, trend_ema100, trend_ema200, trend_adx, "
                    + "trend_plus_di, trend_minus_di, trend_supertrend, momentum_rsi14, momentum_macd, "
                    + "momentum_macd_signal, momentum_macd_histogram, momentum_stochastick, "
                    + "momentum_stochasticd, momentum_cci, momentum_roc, volume_vwap, volume_obv, "
                    + "volume_mfi, volume_cmf, volatility_atr, volatility_bb_upper, volatility_bb_middle, "
                    + "volatility_bb_lower, volatility_bb_width, volatility_percentb, pivot, support1, "
                    + "support2, resistance1, resistance2, created_at) "
                    + "VALUES ( ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                    + "?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)", rowsToInsert);
        }
        long elapsedSeconds = (System.nanoTime() - batchStartedAt) / 1_000_000_000;
        
        System.out.println(" ------------- Indicator backfill batch completed: symbolToken=" + symbolToken+ " timeframe=" + timeframe + " timeTaken=" + elapsedSeconds + " seconds"+ " inserted=" + rowsToInsert.size() );
    }

    private Object[] toInsertParameters(BacktestMarketIndicator indicator) {
        return new Object[]{indicator.getCandle().getId(), indicator.getSymbol(), indicator.getSymbolToken(),
                indicator.getTimeframe(), indicator.getRunId(), indicator.getOrigin(), indicator.getCandleTime(),
                indicator.getTrend_ema(), indicator.getTrend_ema20(), indicator.getTrend_ema50(),
                indicator.getTrend_ema100(), indicator.getTrend_ema200(), indicator.getTrend_adx(),
                indicator.getTrend_plusDi(), indicator.getTrend_minusDi(), indicator.getTrend_supertrend(),
                indicator.getMomentum_rsi14(), indicator.getMomentum_macd(), indicator.getMomentum_macdSignal(),
                indicator.getMomentum_macdHistogram(), indicator.getMomentum_stochasticK(),
                indicator.getMomentum_stochasticD(), indicator.getMomentum_cci(), indicator.getMomentum_roc(),
                indicator.getVolume_vwap(), indicator.getVolume_obv(), indicator.getVolume_mfi(),
                indicator.getVolume_cmf(), indicator.getVolatility_atr(), indicator.getVolatility_bbUpper(),
                indicator.getVolatility_bbMiddle(), indicator.getVolatility_bbLower(),
                indicator.getVolatility_bbWidth(), indicator.getVolatility_percentB(), indicator.getPivot(),
                indicator.getSupport1(), indicator.getSupport2(), indicator.getResistance1(),
                indicator.getResistance2()};
    }

    private record ExistingIndicator(Long id, Long candleId) {
    }

    private Candle toMarketCandle(BacktestCandle source) {
        return Candle.builder().symbol(source.getSymbol()).symbolToken(source.getSymbolToken())
                .exchange(source.getExchange()).subscriptionId(source.getSubscriptionId())
                .subscriptionName(source.getSubscriptionName()).timeframe(source.getTimeframe())
                .candleTime(source.getCandleTime()).endTime(source.getEndTime()).open(source.getOpen())
                .high(source.getHigh()).low(source.getLow()).close(source.getClose()).volume(source.getVolume())
                .ltp(source.getLtp()).build();
    }

    private BacktestMarketIndicator toBacktestIndicator(IndicatorResultDto value, String runId,
                                                        BacktestCandle candle) {
        return BacktestMarketIndicator.builder().symbol(value.getSymbol()).symbolToken(value.getSymbolToken())
                .candle(candle).timeframe(value.getTimeframe()).runId(runId).origin("BACKTEST")
                .candleTime(value.getCandleTime())
                .trend_ema(value.getEma()).trend_ema20(value.getEma20()).trend_ema50(value.getEma50())
                .trend_ema100(value.getEma100()).trend_ema200(value.getEma200()).trend_adx(value.getAdx())
                .trend_plusDi(value.getPlusDi()).trend_minusDi(value.getMinusDi()).trend_supertrend(value.getSupertrend())
                .momentum_rsi14(value.getRsi14()).momentum_macd(value.getMacd()).momentum_macdSignal(value.getMacdSignal())
                .momentum_macdHistogram(value.getMacdHistogram()).momentum_stochasticK(value.getStochasticK())
                .momentum_stochasticD(value.getStochasticD()).momentum_cci(value.getCci()).momentum_roc(value.getRoc())
                .volume_vwap(value.getVwap()).volume_obv(value.getObv()).volume_mfi(value.getMfi()).volume_cmf(value.getCmf())
                .volatility_atr(value.getAtr()).volatility_bbUpper(value.getBbUpper()).volatility_bbMiddle(value.getBbMiddle())
                .volatility_bbLower(value.getBbLower()).volatility_bbWidth(value.getBbWidth()).volatility_percentB(value.getPercentB())
                .pivot(value.getPivot()).support1(value.getSupport1()).support2(value.getSupport2())
                .resistance1(value.getResistance1()).resistance2(value.getResistance2()).build();
    }
}