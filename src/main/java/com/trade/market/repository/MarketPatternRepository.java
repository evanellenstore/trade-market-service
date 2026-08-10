package com.trade.market.repository;

import com.trade.market.entity.MarketPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MarketPatternRepository extends JpaRepository<MarketPattern, Long> {
    List<MarketPattern> findBySymbolAndTimeframeOrderByCandleTimeAsc(String symbol, String timeframe);
    List<MarketPattern> findBySymbolAndTimeframeAndRunIdOrderByCandleTimeAsc(String symbol, String timeframe, String runId);
    List<MarketPattern> findBySymbolAndTimeframeAndCandleTimeBetweenOrderByCandleTimeAsc(String symbol, String timeframe, LocalDateTime startTime, LocalDateTime endTime);
    List<MarketPattern> findBySymbolAndTimeframeAndRunIdAndCandleTimeBetweenOrderByCandleTimeAsc(String symbol, String timeframe, String runId, LocalDateTime startTime, LocalDateTime endTime);
}
