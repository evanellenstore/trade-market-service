package com.trade.market.snapshot.util;

import com.trade.market.entity.Candle;

import java.util.List;

public final class VolumeSpikeCalculator {
    private static final int LOOKBACK = 20;
    private static final double SPIKE_MULTIPLIER = 2.0;

    private VolumeSpikeCalculator() {
    }

    public static boolean calculate(List<Candle> orderedCandles) {
        if (orderedCandles == null || orderedCandles.size() < 2) {
            return false;
        }
        Candle current = orderedCandles.get(orderedCandles.size() - 1);
        int first = Math.max(0, orderedCandles.size() - 1 - LOOKBACK);
        List<Candle> previous = orderedCandles.subList(first, orderedCandles.size() - 1);
        double average = previous.stream().mapToDouble(Candle::getVolume).average().orElse(0);
        return average > 0 && current.getVolume() > SPIKE_MULTIPLIER * average;
    }
}