package com.trade.market.repository;

import com.trade.market.entity.MarketIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MarketIndicatorRepository extends JpaRepository<MarketIndicator, Long> {
    List<MarketIndicator> findBySymbolAndTimeframeOrderByCandleTimeAsc(String symbol, String timeframe);
    List<MarketIndicator> findBySymbolAndTimeframeAndRunIdOrderByCandleTimeAsc(String symbol, String timeframe, String runId);
    List<MarketIndicator> findBySymbolAndTimeframeAndCandleTimeBetweenOrderByCandleTimeAsc(String symbol, String timeframe, LocalDateTime startTime, LocalDateTime endTime);
    List<MarketIndicator> findBySymbolAndTimeframeAndRunIdAndCandleTimeBetweenOrderByCandleTimeAsc(String symbol, String timeframe, String runId, LocalDateTime startTime, LocalDateTime endTime);
}
