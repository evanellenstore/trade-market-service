package com.trade.market.dto;

import com.trade.market.entity.Candle;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PatternMessage {
    private String symbol;
    private String timeframe;
    private java.time.LocalDateTime candleTime;
    private String subscriptionId;
    private String subscriptionName;
    private String runId;
    private String patternName;
    private String origin;
    private List<Candle> candles;
}
