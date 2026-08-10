package com.trade.market.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "market_patterns", indexes = {
        @Index(name = "idx_market_patterns_symbol_token_timeframe_runid_candle_time", columnList = "symbolToken, timeframe, runId, candle_time")
})
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketPattern {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String symbol;
    private String symbolToken;
    private String timeframe;
    private String runId;

    @Column(name = "candle_time")
    private LocalDateTime candleTime;

    private boolean hammer;
    private boolean invertedHammer;
    private boolean shootingStar;
    private boolean doji;
    private boolean bullishEngulfing;
    private boolean bearishEngulfing;
    private boolean morningStar;
    private boolean eveningStar;
    private boolean haramiBullish;
    private boolean haramiBearish;

    private boolean doubleTop;
    private boolean doubleBottom;
    private boolean headAndShoulders;
    private boolean inverseHeadAndShoulders;
    private boolean ascendingTriangle;
    private boolean descendingTriangle;
    private boolean symmetricalTriangle;
    private boolean bullFlag;
    private boolean bearFlag;
    private boolean cupAndHandle;
    private boolean risingWedge;
    private boolean fallingWedge;
    private boolean rectangle;
    private boolean breakout;
    private boolean breakdown;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
