package com.trade.market.repository;

import com.trade.market.entity.BacktestCandle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface BacktestCandleRepository extends JpaRepository<BacktestCandle, Long> {
    List<BacktestCandle> findBySymbolAndTimeframeOrderByCandleTimeAsc(String symbol, String timeframe);

    @Query("select distinct c.symbol from BacktestCandle c")
    List<String> findDistinctSymbols();

    @Query("select distinct c.timeframe from BacktestCandle c where c.symbol = :symbol")
    List<String> findDistinctTimeframes(@Param("symbol") String symbol);
}