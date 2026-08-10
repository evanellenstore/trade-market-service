package com.trade.market.datasource;

import com.trade.market.entity.Candle;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Backtest data source owns a single symbol history and exposes candles in a newset-first contract while
 * guaranteeing that no future candles are visible during a replay step.
 */
public class BacktestMarketDataSource {

    private final String symbol;
    private final String timeframe;
    private final List<Candle> historyOldestFirst;
    private final Iterator<Candle> replayIterator;
    private final List<Candle> trailingWindowNewestFirst = new ArrayList<>();

    public BacktestMarketDataSource(String symbol, String timeframe, List<Candle> historyOldestFirst) {
        this.symbol = symbol;
        this.timeframe = timeframe;
        this.historyOldestFirst = List.copyOf(historyOldestFirst);
        if (!isChronological(historyOldestFirst)) {
            throw new IllegalArgumentException("Backtest history must be provided oldest-first");
        }
        this.replayIterator = this.historyOldestFirst.iterator();
    }

    public String getSymbol() {
        return symbol;
    }

    public String getTimeframe() {
        return timeframe;
    }

    public boolean hasNext() {
        return replayIterator.hasNext();
    }

    public List<Candle> advance(int limit) {
        if (!hasNext()) {
            throw new NoSuchElementException("No more candles available for backtest");
        }

        Candle nextCandle = replayIterator.next();
        addToTrailingWindow(nextCandle, limit);
        return getTrailingWindow(limit);
    }

    private void addToTrailingWindow(Candle candle, int limit) {
        trailingWindowNewestFirst.add(0, candle);
        if (trailingWindowNewestFirst.size() > limit) {
            trailingWindowNewestFirst.remove(trailingWindowNewestFirst.size() - 1);
        }
    }

    private List<Candle> getTrailingWindow(int limit) {
        return List.copyOf(trailingWindowNewestFirst);
    }

    private boolean isChronological(List<Candle> candles) {
        for (int i = 1; i < candles.size(); i++) {
            if (candles.get(i - 1).getCandleTime().isAfter(candles.get(i).getCandleTime())) {
                return false;
            }
        }
        return true;
    }

    public List<Candle> getHistoryOldestFirst() {
        return historyOldestFirst;
    }
}
