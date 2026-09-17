package com.trade.market.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class IndicatorBackfillRequest {
    @NotEmpty
    private List<@NotBlank String> symbolTokens;
    @NotBlank
    private String timeframe;
    private String source = "BACKTEST";
}