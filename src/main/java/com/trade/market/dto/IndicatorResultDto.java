package com.trade.market.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IndicatorResultDto {
    private String symbol;
    private String symbolToken;
    private String timeframe;
    private double ema20;
    private double ema50;
    private double ema200;
    private double rsi14;
    private double macd;
    private double signal;
    private double histogram;
    private double atr;
    private double adx;
    private double vwap;
    private double supertrend;
    private double bbUpper;
    private double bbMiddle;
    private double bbLower;
    private double pivot;
    private double support1;
    private double support2;
   private double resistance1;
    private double resistance2;
}
