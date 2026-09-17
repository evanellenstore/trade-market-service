package com.trade.market.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "market_candles_backtest", indexes = {
        @Index(name = "idx_market_candles_backtest_symbol_token_timeframe_candle_time",
                columnList = "symbolToken,timeframe,candle_time")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_market_candles_backtest_symbol_timeframe_time",
                columnNames = {"symbolToken", "timeframe", "candle_time"})
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BacktestCandle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String symbol;
    private String symbolToken;
    private String exchange;
    private String subscriptionId;
    private String subscriptionName;
    private String timeframe;
    @Column(name = "candle_time")
    private LocalDateTime candleTime;
    private LocalDateTime endTime;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
    private double ltp;
    @Column(name = "created_at", columnDefinition = "TIMESTAMP DEFAULT CURRENT_TIMESTAMP")
    private LocalDateTime createdAt;
}