package com.trade.market.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "market_candles", indexes = {
    @Index(name = "idx_market_candles_symbol_token_timeframe_candle_time", columnList = "symbolToken, timeframe, candle_time")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Candle {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String symbol;
    private String symbolToken;
    private String exchange;
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
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public LocalDateTime getStartTime() {
        return candleTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.candleTime = startTime;
    }
}
