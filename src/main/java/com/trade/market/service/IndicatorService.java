package com.trade.market.service;

import com.trade.market.dto.IndicatorResultDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
public class IndicatorService {

    public IndicatorResultDto calculateIndicators(String symbol, String timeframe, List<Double> closePrices) {
        if (closePrices == null || closePrices.isEmpty()) {
            return IndicatorResultDto.builder().symbol(symbol).timeframe(timeframe).build();
        }

        double ema20 = calculateEMA(closePrices, 20);
        double ema50 = calculateEMA(closePrices, 50);
        double ema200 = calculateEMA(closePrices, 200);
        double rsi14 = calculateRSI(closePrices, 14);
        double macd = calculateMacd(closePrices);
        double signal = calculateSignal(closePrices);
        double histogram = macd - signal;
        double atr = calculateAtr(closePrices);
        double adx = calculateAdx(closePrices);
        double vwap = calculateVwap(closePrices);
        double supertrend = calculateSupertrend(closePrices);
        double bbUpper = calculateBollingerUpper(closePrices);
        double bbMiddle = calculateBollingerMiddle(closePrices);
        double bbLower = calculateBollingerLower(closePrices);
        double pivot = calculatePivot(closePrices);
        double support1 = pivot - (highLowRange(closePrices) / 2.0);
        double support2 = pivot - (highLowRange(closePrices) * 1.0);
        double resistance1 = pivot + (highLowRange(closePrices) / 2.0);
        double resistance2 = pivot + (highLowRange(closePrices) * 1.0);

        return IndicatorResultDto.builder()
                .symbol(symbol)
                .timeframe(timeframe)
                .ema20(ema20)
                .ema50(ema50)
                .ema200(ema200)
                .rsi14(rsi14)
                .macd(macd)
                .signal(signal)
                .histogram(histogram)
                .atr(atr)
                .adx(adx)
                .vwap(vwap)
                .supertrend(supertrend)
                .bbUpper(bbUpper)
                .bbMiddle(bbMiddle)
                .bbLower(bbLower)
                .pivot(pivot)
                .support1(support1)
                .support2(support2)
                .resistance1(resistance1)
                .resistance2(resistance2)
                .build();
    }

    private double calculateEMA(List<Double> prices, int period) {
        if (prices.isEmpty()) {
            return 0.0;
        }
        if (prices.size() < period) {
            return prices.get(prices.size() - 1);
        }

        double multiplier = 2.0 / (period + 1);
        double ema = prices.subList(0, period).stream().mapToDouble(Double::doubleValue).average().orElse(0.0);

        for (int i = period; i < prices.size(); i++) {
            ema = (prices.get(i) - ema) * multiplier + ema;
        }
        return ema;
    }

    private double calculateRSI(List<Double> prices, int period) {
        if (prices.size() < period + 1) {
            return 50.0;
        }

        double gain = 0.0;
        double loss = 0.0;
        for (int i = 1; i <= period; i++) {
            double diff = prices.get(i) - prices.get(i - 1);
            if (diff > 0) {
                gain += diff;
            } else {
                loss += Math.abs(diff);
            }
        }

        double avgGain = gain / period;
        double avgLoss = loss / period;
        if (avgLoss == 0) {
            return 100.0;
        }

        double rs = avgGain / avgLoss;
        return 100.0 - (100.0 / (1.0 + rs));
    }

    private double calculateMacd(List<Double> prices) {
        double ema12 = calculateEMA(prices, 12);
        double ema26 = calculateEMA(prices, 26);
        return ema12 - ema26;
    }

    private double calculateSignal(List<Double> prices) {
        return calculateEMA(new ArrayList<>(prices.subList(Math.max(prices.size() - 9, 0), prices.size())), 9);
    }

    private double calculateAtr(List<Double> prices) {
        if (prices.size() < 2) {
            return 0.0;
        }
        double sum = 0.0;
        for (int i = 1; i < prices.size(); i++) {
            sum += Math.abs(prices.get(i) - prices.get(i - 1));
        }
        return sum / Math.max(1, prices.size() - 1);
    }

    private double calculateAdx(List<Double> prices) {
        if (prices.size() < 2) {
            return 0.0;
        }
        return calculateRSI(prices, 14) / 2.0;
    }

    private double calculateVwap(List<Double> prices) {
        if (prices.isEmpty()) {
            return 0.0;
        }
        return prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double calculateSupertrend(List<Double> prices) {
        if (prices.isEmpty()) {
            return 0.0;
        }
        return prices.get(prices.size() - 1);
    }

    private double calculateBollingerUpper(List<Double> prices) {
        double mid = calculateBollingerMiddle(prices);
        return mid + (calculateStdDev(prices) * 2.0);
    }

    private double calculateBollingerMiddle(List<Double> prices) {
        if (prices.isEmpty()) {
            return 0.0;
        }
        return prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
    }

    private double calculateBollingerLower(List<Double> prices) {
        double mid = calculateBollingerMiddle(prices);
        return mid - (calculateStdDev(prices) * 2.0);
    }

    private double calculatePivot(List<Double> prices) {
        if (prices.isEmpty()) {
            return 0.0;
        }
        return prices.get(prices.size() - 1);
    }

    private double highLowRange(List<Double> prices) {
        if (prices.isEmpty()) {
            return 0.0;
        }
        double min = prices.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = prices.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        return max - min;
    }

    private double calculateStdDev(List<Double> prices) {
        if (prices.isEmpty()) {
            return 0.0;
        }
        double mean = prices.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double variance = prices.stream().mapToDouble(price -> Math.pow(price - mean, 2)).average().orElse(0.0);
        return Math.sqrt(variance);
    }
}
