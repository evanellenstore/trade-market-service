package com.trade.market.repository;

import com.trade.market.entity.BacktestMarketIndicator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;

public interface BacktestMarketIndicatorRepository extends JpaRepository<BacktestMarketIndicator, Long> {
    boolean existsBySymbolAndTimeframeAndCandleTimeAndRunId(
            String symbol, String timeframe, LocalDateTime candleTime, String runId);
}