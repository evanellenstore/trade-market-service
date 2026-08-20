package com.trade.market.snapshot.util;

public final class MarketRegimeCalculator {
    private static final double HIGH_ATR_PERCENT = 0.03;
    private static final double LOW_ATR_PERCENT = 0.01;

    private MarketRegimeCalculator() {
    }

    /** Uses ATR as a percentage of price so the rule works across differently priced symbols. */
    public static String calculate(double adx, double atr, double price) {
        double atrPercent = price > 0 ? atr / price : 0;
        if (atrPercent >= HIGH_ATR_PERCENT) {
            return "VOLATILE";
        }
        if (atrPercent <= LOW_ATR_PERCENT) {
            return "LOW_VOLATILITY";
        }
        return adx >= 25 ? "TRENDING" : "RANGING";
    }
}