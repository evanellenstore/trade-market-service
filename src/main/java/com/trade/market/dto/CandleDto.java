package com.trade.market.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandleDto {
    private Long id;
    private String symbol;
    private String exchange;
    private String timeframe;
    private double open;
    private double high;
    private double low;
    private double close;
    private double volume;
    private String startTime;
    private String endTime;
}
