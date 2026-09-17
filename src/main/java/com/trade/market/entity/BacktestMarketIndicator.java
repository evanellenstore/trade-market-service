package com.trade.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.PrePersist;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "market_indicators_backtest")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestMarketIndicator {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String symbol;
    private String symbolToken;
    private String timeframe;
    private String runId;
    private String origin;
    @Column(name = "candle_time")
    private LocalDateTime candleTime;
    private double trend_ema;
    private double trend_ema20;
    private double trend_ema50;
    private double trend_ema100;
    private double trend_ema200;
    private double trend_adx;
    private double trend_plusDi;
    private double trend_minusDi;
    private double trend_supertrend;
    private double momentum_rsi14;
    private double momentum_macd;
    private double momentum_macdSignal;
    private double momentum_macdHistogram;
    private double momentum_stochasticK;
    private double momentum_stochasticD;
    private double momentum_cci;
    private double momentum_roc;
    private double volume_vwap;
    private double volume_obv;
    private double volume_mfi;
    private double volume_cmf;
    private double volatility_atr;
    private double volatility_bbUpper;
    private double volatility_bbMiddle;
    private double volatility_bbLower;
    private double volatility_bbWidth;
    private double volatility_percentB;
    private double pivot;
    private double support1;
    private double support2;
    private double resistance1;
    private double resistance2;
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}