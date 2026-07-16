package com.trade.market.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "candles", indexes = {
    @Index(name = "idx_symbol_timeframe", columnList = "symbol, timeframe"),
    @Index(name = "idx_start_time", columnList = "start_time")
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
    private String exchange;
    private String timeframe;
    private LocalDateTime startTime;
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
}
