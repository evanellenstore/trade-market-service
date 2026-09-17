package com.trade.market.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class IndicatorBackfillStatusDto {
    private String symbolToken;
    private Long indicatorCount;
    private LocalDateTime updatedAt;
}