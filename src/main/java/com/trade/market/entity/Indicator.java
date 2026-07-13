package com.trade.market.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "market_indicators", indexes = {
        @Index(name = "idx_indicator_symbol_timeframe", columnList = "symbol, timeframe")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Indicator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
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

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
