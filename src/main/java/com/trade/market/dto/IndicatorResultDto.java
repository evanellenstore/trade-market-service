package com.trade.market.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorResultDto {
    private String symbol;
    private String symbolToken;
    private String timeframe;
    private String runId;
    private LocalDateTime candleTime;
    //========= TREND =========
    private double ema;
    private double ema20;
    private double ema50;
    private double ema100;
    private double ema200;
    private double adx;
    private double plusDi;
    private double minusDi;
    private double supertrend;

    // ========= MOMENTUM =========

    private double rsi14;
    private double macd;
    private double macdSignal;
    private double macdHistogram;
    private double stochasticK;
    private double stochasticD;
    private double cci;
    private double roc;

    // ========= VOLUME =========

    private double vwap;
    private double obv;
    private double mfi;
    private double cmf;

    // ========= VOLATILITY =========

    private double atr;
    private double bbUpper;
    private double bbMiddle;
    private double bbLower;
    private double bbWidth;
    private double percentB;

    // ========= SUPPORT/RESISTANCE =========
    
    
    private double pivot;
    private double support1;
    private double support2;
    private double resistance1;
    private double resistance2;
}
