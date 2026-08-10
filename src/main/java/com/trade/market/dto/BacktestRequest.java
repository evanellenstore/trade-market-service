package com.trade.market.dto;

import lombok.Data;

@Data
public class BacktestRequest {
    private String symbol;
    private String timeframe;
    private String runId;
    private String startTime;
    private String endTime;
}
