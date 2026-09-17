package com.trade.market.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class IndicatorBackfillRequest {
    @NotBlank
    private String symbol;
    @NotBlank
    private String timeframe;
    private String source = "BACKTEST";
}