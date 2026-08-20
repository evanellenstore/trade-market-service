package com.trade.market.snapshot.util;

import com.trade.market.dto.IndicatorResultDto;

public final class SignalStrengthCalculator {
    private SignalStrengthCalculator() {
    }

    public static String calculate(String trend, IndicatorResultDto indicators) {
        int score = 0;
        boolean bullish = "BULLISH".equals(trend);
        boolean bearish = "BEARISH".equals(trend);
        boolean macdConfirms = bullish ? indicators.getMacd() > indicators.getMacdSignal()
                : bearish && indicators.getMacd() < indicators.getMacdSignal();
        boolean rsiConfirms = bullish ? indicators.getRsi14() >= 50 && indicators.getRsi14() < 70
                : bearish && indicators.getRsi14() > 30 && indicators.getRsi14() <= 50;
        if (bullish || bearish) score++;
        if (macdConfirms) score++;
        if (rsiConfirms) score++;
        if (indicators.getAdx() >= 25) score++;
        if (indicators.getAdx() > 40 && macdConfirms && rsiConfirms) score++;
        return switch (score) {
            case 4, 5 -> "VERY_HIGH";
            case 3 -> "HIGH";
            case 2 -> "MEDIUM";
            default -> "LOW";
        };
    }
}