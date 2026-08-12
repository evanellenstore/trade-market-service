package com.trade.market.dto;

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
    private String runId;
    private String patternName;
    private String origin;
}
