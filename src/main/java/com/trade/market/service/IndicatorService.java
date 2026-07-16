package com.trade.market.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.CCIIndicator;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.ROCIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.adx.ADXIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
import org.ta4j.core.indicators.volume.ChaikinMoneyFlowIndicator;
import org.ta4j.core.indicators.volume.OnBalanceVolumeIndicator;
import org.ta4j.core.indicators.volume.VWAPIndicator;
import org.ta4j.core.num.DecimalNum;
import org.ta4j.core.num.Num;

import com.trade.market.dto.IndicatorResultDto;
import com.trade.market.indicator.BarSeriesManager;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndicatorService {

    private final BarSeriesManager barSeriesManager;

    public IndicatorResultDto calculateIndicators(String symbol, String timeframe, String symbolToken, LocalDateTime candleTime, List<Double> closePrices) {
        if (closePrices == null || closePrices.isEmpty()) {
            return IndicatorResultDto.builder()
                    .symbol(symbol)
                    .symbolToken(symbolToken)
                    .timeframe(timeframe)
                    .candleTime(candleTime)
                    .build();
        }

        BarSeries series = barSeriesManager.getSeries(symbol, timeframe);
        if (series == null || series.getBarCount() == 0) {
            // Fallback to simple calculation using last values when no TA4J series available
            double last = closePrices.get(closePrices.size() - 1);
            double pivot = last;
            double highLowRange = closePrices.stream().mapToDouble(Double::doubleValue).max().orElse(pivot) -
                    closePrices.stream().mapToDouble(Double::doubleValue).min().orElse(pivot);
            double support1 = pivot - (highLowRange / 2.0);
            double support2 = pivot - highLowRange;
            double resistance1 = pivot + (highLowRange / 2.0);
            double resistance2 = pivot + highLowRange;

            return IndicatorResultDto.builder()
                    .symbol(symbol)
                    .symbolToken(symbolToken)
                    .timeframe(timeframe)
                    .candleTime(candleTime)
                    //========= TREND =========
                    .ema(last)
                    .ema20(last)
                    .ema50(last)
                    .ema100(last)
                    .ema200(last)
                    .adx(0.0)
                    .plusDi(0.0)
                    .minusDi(0.0)
                    .supertrend(0.0)
                    // ========= MOMENTUM =========
                    .rsi14(50.0)
                    .macd(0.0)
                    .macdSignal(0.0)
                    .macdHistogram(0.0)
                    .stochasticK(0.0)
                    .stochasticD(0.0)
                    .cci(0.0)
                    .roc(0.0)
                    // ========= VOLUME =========
                    .vwap(last)
                    .obv(0.0)
                    .mfi(0.0)
                    .cmf(0.0)
                    // ========= VOLATILITY =========
                    .atr(0.0)
                    .bbUpper(last)
                    .bbMiddle(last)
                    .bbLower(last)
                    .bbWidth(0.0)
                    .percentB(0.0)
                    // ========= SUPPORT/RESISTANCE =========
                    .pivot(pivot)
                    .support1(support1)
                    .support2(support2)
                    .resistance1(resistance1)
                    .resistance2(resistance2)
                    .build();
        }

        ClosePriceIndicator close = new ClosePriceIndicator(series);

        EMAIndicator ema = new EMAIndicator(close, 10);
        EMAIndicator ema20 = new EMAIndicator(close, 20);
        EMAIndicator ema50 = new EMAIndicator(close, 50);
        EMAIndicator ema100 = new EMAIndicator(close, 100);
        EMAIndicator ema200 = new EMAIndicator(close, 200);
        RSIIndicator rsi14 = new RSIIndicator(close, 14);

        MACDIndicator macd = new MACDIndicator(close, 12, 26);
        EMAIndicator macdSignal = new EMAIndicator(macd, 9);
        DifferenceIndicator macdHist = new DifferenceIndicator(macd, macdSignal);

        StandardDeviationIndicator sd20 = new StandardDeviationIndicator(close, 20);

        BollingerBandsMiddleIndicator bbMid = new BollingerBandsMiddleIndicator(close);
        BollingerBandsUpperIndicator bbUpper = new BollingerBandsUpperIndicator(bbMid, sd20, DecimalNum.valueOf(2));
        BollingerBandsLowerIndicator bbLower = new BollingerBandsLowerIndicator(bbMid, sd20, DecimalNum.valueOf(2));

        VWAPIndicator vwap = new VWAPIndicator(series, 14);
        ADXIndicator adx = new ADXIndicator(series, 14);
        // TA4J 0.14 does not expose PlusDI/MinusDI indicators under adx package
        // keep placeholders (computed as 0.0) for now to avoid compile errors
        CCIIndicator cci = new CCIIndicator(series, 20);
        ROCIndicator roc = new ROCIndicator(close, 12);
        OnBalanceVolumeIndicator obv = new OnBalanceVolumeIndicator(series);
        ChaikinMoneyFlowIndicator cmf = new ChaikinMoneyFlowIndicator(series, 20);

        int idx = series.getEndIndex();

        //========= TREND =========
        Num latestEma = ema.getValue(idx);
        Num latestEma20 = ema20.getValue(idx);
        Num latestEma50 = ema50.getValue(idx);
        Num latestEma100 = ema100.getValue(idx);
        Num latestEma200 = ema200.getValue(idx);
        Num latestAdx = adx.getValue(idx);
        Num latestPlusDi = DecimalNum.valueOf(0);
        Num latestMinusDi = DecimalNum.valueOf(0);
        // ========= MOMENTUM =========
        Num latestRsi14 = rsi14.getValue(idx);
        Num latestMacd = macd.getValue(idx);
        Num latestSignal = macdSignal.getValue(idx);
        Num latestHist = macdHist.getValue(idx);
        Num latestCci = cci.getValue(idx);
        Num latestRoc = roc.getValue(idx);
        // ========= VOLUME =========
        Num latestVwap = vwap.getValue(idx);
        Num latestBbUpper = bbUpper.getValue(idx);
        Num latestBbMid = bbMid.getValue(idx);
        Num latestBbLower = bbLower.getValue(idx);
        // TA4J 0.14 in this environment doesn't expose PlusDI/MinusDI; use zero defaults
        Num latestObv = obv.getValue(idx);
        Num latestCmf = cmf.getValue(idx);
        double latestAtr = 0.0;
        double latestMfi = 0.0;

        double pivot = closePrices.get(closePrices.size() - 1);
        double highest = closePrices.stream().mapToDouble(Double::doubleValue).max().orElse(pivot);
        double lowest = closePrices.stream().mapToDouble(Double::doubleValue).min().orElse(pivot);
        double highLowRange = highest - lowest;
        double support1 = pivot - (highLowRange / 2.0);
        double support2 = pivot - highLowRange;
        double resistance1 = pivot + (highLowRange / 2.0);
        double resistance2 = pivot + highLowRange;
        double bbWidth = latestBbUpper.doubleValue() - latestBbLower.doubleValue();
        double percentB = bbWidth != 0.0 ? (latestEma20.doubleValue() - latestBbLower.doubleValue()) / bbWidth : 0.0;


        return IndicatorResultDto.builder()
                .symbol(symbol)
                .symbolToken(symbolToken)
                .timeframe(timeframe)
                .candleTime(candleTime)
                //========= TREND =========
                .ema(latestEma.doubleValue())
                .ema20(latestEma20.doubleValue())
                .ema50(latestEma50.doubleValue())
                .ema100(latestEma100.doubleValue())
                .ema200(latestEma200.doubleValue())
                .adx(latestAdx.doubleValue())
                .plusDi(latestPlusDi.doubleValue())
                .minusDi(latestMinusDi.doubleValue())
                .supertrend(0.0)
                // ========= MOMENTUM =========
                .rsi14(latestRsi14.doubleValue())
                .macd(latestMacd.doubleValue())
                .macdSignal(latestSignal.doubleValue())
                .macdHistogram(latestHist.doubleValue())
                .stochasticK(0.0)
                .stochasticD(0.0)
                .cci(latestCci.doubleValue())
                .roc(latestRoc.doubleValue())
                // ========= VOLUME =========
                .vwap(latestVwap.doubleValue())
                .obv(latestObv.doubleValue())
                .mfi(latestMfi)
                .cmf(latestCmf.doubleValue())
                // ========= VOLATILITY =========
                .atr(latestAtr)
                .bbUpper(latestBbUpper.doubleValue())
                .bbMiddle(latestBbMid.doubleValue())
                .bbLower(latestBbLower.doubleValue())
                .bbWidth(bbWidth)
                .percentB(percentB)
                // ========= SUPPORT/RESISTANCE =========
                .pivot(pivot)
                .support1(support1)
                .support2(support2)
                .resistance1(resistance1)
                .resistance2(resistance2)
                .build();
    }
}
