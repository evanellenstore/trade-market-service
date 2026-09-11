package com.trade.market.snapshot.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/** Consolidated market context published after a candle is enriched. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketSnapshot {
    private String symbol;
    private String symbolToken;
    private String subscriptionId;
    private String subscriptionName;
    private String exchange;
    private String timeframe;
    private double price;
    private String trend;
    private String trendStrength;
    private String marketRegime;
    private boolean volumeSpike;
    private double rsi14;
    private double adx;
    private double ema20;
    private double ema50;
    private double ema100;
    private double ema200;
    private double macd;
    private double macdSignal;
    private double macdHistogram;
    private double atr;
    private double vwap;
    private String supertrendSignal;
    private String pattern;
    private double support1;
    private double resistance1;
    private String signalStrength;
    private LocalDateTime snapshotTime;
    private String runId;
    private String origin;
}