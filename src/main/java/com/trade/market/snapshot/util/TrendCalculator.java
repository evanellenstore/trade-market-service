package com.trade.market.snapshot.util;

import com.trade.market.dto.IndicatorResultDto;

public final class TrendCalculator {
    private TrendCalculator() {
    }

    public static String calculate(IndicatorResultDto indicators) {
        if (indicators.getEma20() > indicators.getEma50()
                && indicators.getEma50() > indicators.getEma200()) {
            return "BULLISH";
        }
        if (indicators.getEma20() < indicators.getEma50()
                && indicators.getEma50() < indicators.getEma200()) {
            return "BEARISH";
        }
        return "SIDEWAYS";
    }
}