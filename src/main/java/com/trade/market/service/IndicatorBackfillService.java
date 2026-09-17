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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndicatorBackfillService {
    private final BacktestCandleRepository backtestCandleRepository;
    private final BacktestMarketIndicatorRepository backtestIndicatorRepository;
    private final IndicatorService indicatorService;
    private final BarSeriesManager barSeriesManager;

    public String start(IndicatorBackfillRequest request) {
        String source = request.getSource() == null ? "BACKTEST" : request.getSource().trim().toUpperCase();
        if (!"BACKTEST".equals(source)) {
            throw new IllegalArgumentException("Only BACKTEST indicator backfill is supported");
        }
        String runId = "indicator-backfill-" + UUID.randomUUID();
        backfillAsync(request.getSymbol(), request.getTimeframe(), runId);
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
        for (String symbol : backtestCandleRepository.findDistinctSymbols()) {
            for (String timeframe : backtestCandleRepository.findDistinctTimeframes(symbol)) {
                backfill(symbol, timeframe, runId);
            }
        }
    }

    @Async
    public void backfillAsync(String symbol, String timeframe, String runId) {
        backfill(symbol, timeframe, runId);
    }

    private void backfill(String symbol, String timeframe, String runId) {
        List<BacktestCandle> candles = backtestCandleRepository.findBySymbolAndTimeframeOrderByCandleTimeAsc(symbol, timeframe);
        if (candles.isEmpty()) {
            log.info("No backtest candles found for symbol={} timeframe={}", symbol, timeframe);
            return;
        }

        String seriesKey = symbol + "::" + timeframe + "::" + runId;
        List<Double> closes = new ArrayList<>();
        for (BacktestCandle backtestCandle : candles) {
            closes.add(backtestCandle.getClose());
            Candle candle = toMarketCandle(backtestCandle);
            barSeriesManager.addCandleByKey(seriesKey, timeframe, candle);
            IndicatorResultDto result = indicatorService.calculateIndicatorsBySeriesKey(
                    symbol, timeframe, seriesKey, candle.getSymbolToken(), candle.getCandleTime(), closes);
                if (!backtestIndicatorRepository.existsBySymbolTokenAndTimeframeAndCandleTime(
                    candle.getSymbolToken(), timeframe, candle.getCandleTime())) {
                backtestIndicatorRepository.save(toBacktestIndicator(result, runId));
            }
        }
        log.info("Completed indicator backfill: symbol={} timeframe={} candles={} runId={}",
                symbol, timeframe, candles.size(), runId);
    }

    private Candle toMarketCandle(BacktestCandle source) {
        return Candle.builder().symbol(source.getSymbol()).symbolToken(source.getSymbolToken())
                .exchange(source.getExchange()).subscriptionId(source.getSubscriptionId())
                .subscriptionName(source.getSubscriptionName()).timeframe(source.getTimeframe())
                .candleTime(source.getCandleTime()).endTime(source.getEndTime()).open(source.getOpen())
                .high(source.getHigh()).low(source.getLow()).close(source.getClose()).volume(source.getVolume())
                .ltp(source.getLtp()).build();
    }

    private BacktestMarketIndicator toBacktestIndicator(IndicatorResultDto value, String runId) {
        return BacktestMarketIndicator.builder().symbol(value.getSymbol()).symbolToken(value.getSymbolToken())
                .timeframe(value.getTimeframe()).runId(runId).origin("BACKTEST").candleTime(value.getCandleTime())
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