package com.trade.market.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.ta4j.core.BarSeries;
import org.ta4j.core.indicators.EMAIndicator;
import org.ta4j.core.indicators.MACDIndicator;
import org.ta4j.core.indicators.RSIIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsLowerIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsMiddleIndicator;
import org.ta4j.core.indicators.bollinger.BollingerBandsUpperIndicator;
import org.ta4j.core.indicators.helpers.ClosePriceIndicator;
import org.ta4j.core.indicators.helpers.DifferenceIndicator;
import org.ta4j.core.indicators.statistics.StandardDeviationIndicator;
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

    public IndicatorResultDto calculateIndicators(String symbol, String timeframe, List<Double> closePrices) {
        if (closePrices == null || closePrices.isEmpty()) {
            return IndicatorResultDto.builder().symbol(symbol).timeframe(timeframe).build();
        }

        BarSeries series = barSeriesManager.getSeries(symbol, timeframe);
        if (series == null || series.getBarCount() == 0) {
            // Fallback to simple calculation using last values when no TA4J series available
            double last = closePrices.get(closePrices.size() - 1);
            return IndicatorResultDto.builder()
                    .symbol(symbol)
                    .timeframe(timeframe)
                    .ema20(last)
                    .ema50(last)
                    .ema200(last)   
                    //.rsi14(last)
                    //.macd(0.0)
                    //.signal(0.0)
                    //.histogram(0.0)
                   // .atr(0.0)
                   // .adx(0.0)
                   // .vwap(last)
                   // .supertrend(last)
                   // .bbUpper(last)
                   // .bbMiddle(last)
                   // .bbLower(last)
                   // .pivot(last)
                   // .support1(last)
                   // .support2(last)
                   // .resistance1(last)
                   // .resistance2(last)
                    .build();
        }

        ClosePriceIndicator close = new ClosePriceIndicator(series);

        EMAIndicator ema20 = new EMAIndicator(close, 20);
        EMAIndicator ema50 = new EMAIndicator(close, 50);
        EMAIndicator ema200 = new EMAIndicator(close, 200);
       // RSIIndicator rsi14 = new RSIIndicator(close, 14);

        //MACDIndicator macd = new MACDIndicator(close, 12, 26);
        //EMAIndicator macdSignal = new EMAIndicator(macd, 9);
        //DifferenceIndicator macdHist = new DifferenceIndicator(macd, macdSignal);

        //StandardDeviationIndicator sd20 = new StandardDeviationIndicator(close, 20);

       // BollingerBandsMiddleIndicator bbMid = new BollingerBandsMiddleIndicator(close);
       // BollingerBandsUpperIndicator bbUpper = new BollingerBandsUpperIndicator(bbMid, sd20, DecimalNum.valueOf(2));
       // BollingerBandsLowerIndicator bbLower = new BollingerBandsLowerIndicator(bbMid, sd20, DecimalNum.valueOf(2));

        //VWAPIndicator vwap = new VWAPIndicator(series, 14);

        int idx = series.getEndIndex();
        Num latestEma20 = ema20.getValue(idx);
        Num latestEma50 = ema50.getValue(idx);
        Num latestEma200 = ema200.getValue(idx);
       // Num latestRsi14 = rsi14.getValue(idx);
       // Num latestMacd = macd.getValue(idx);
       // Num latestSignal = macdSignal.getValue(idx);
       // Num latestHist = macdHist.getValue(idx);
       // Num latestVwap = vwap.getValue(idx);
       // Num latestBbUpper = bbUpper.getValue(idx);
       // Num latestBbMid = bbMid.getValue(idx);
       // Num latestBbLower = bbLower.getValue(idx);

        double pivot = closePrices.get(closePrices.size() - 1);
        double highLowRange = closePrices.stream().mapToDouble(Double::doubleValue).max().orElse(pivot) -
                closePrices.stream().mapToDouble(Double::doubleValue).min().orElse(pivot);

        return IndicatorResultDto.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .ema20(latestEma20.doubleValue())
                .ema50(latestEma50.doubleValue())
                .ema200(latestEma200.doubleValue())
               // .rsi14(latestRsi14.doubleValue())
               // .macd(latestMacd.doubleValue())
               // .signal(latestSignal.doubleValue())
               // .histogram(latestHist.doubleValue())
               // .atr(0.0) // keep existing ATR calculation if needed
               // .adx(0.0)
               // .vwap(latestVwap.doubleValue())
               // .supertrend(close.getValue(idx).doubleValue())
               // .bbUpper(latestBbUpper.doubleValue())
               // .bbMiddle(latestBbMid.doubleValue())
               // .bbLower(latestBbLower.doubleValue())
               // .pivot(pivot)
               // .support1(pivot - (highLowRange / 2.0))
               // .support2(pivot - highLowRange)
               // .resistance1(pivot + (highLowRange / 2.0))
               // .resistance2(pivot + highLowRange)
                .build();
    }
}
