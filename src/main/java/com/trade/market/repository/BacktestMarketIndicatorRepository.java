package com.trade.market.repository;

import com.trade.market.entity.BacktestMarketIndicator;
import com.trade.market.dto.IndicatorBackfillStatusDto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BacktestMarketIndicatorRepository extends JpaRepository<BacktestMarketIndicator, Long> {
        boolean existsBySymbolTokenAndTimeframeAndCandleTime(
            String symbolToken, String timeframe, LocalDateTime candleTime);

        @Query("select new com.trade.market.dto.IndicatorBackfillStatusDto(i.symbolToken, count(i.id), max(i.createdAt)) "
            + "from BacktestMarketIndicator i "
            + "where i.symbolToken in :symbolTokens and i.timeframe = :timeframe and i.origin = 'BACKTEST' "
            + "group by i.symbolToken")
        List<IndicatorBackfillStatusDto> findBackfillStatus(
            @Param("symbolTokens") List<String> symbolTokens,
            @Param("timeframe") String timeframe);
}