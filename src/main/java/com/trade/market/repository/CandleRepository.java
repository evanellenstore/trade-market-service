package com.trade.market.repository;

import com.trade.market.entity.Candle;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CandleRepository extends JpaRepository<Candle, Long> {
    
    /**
     * Find candles by symbol and timeframe
     */
    List<Candle> findBySymbolAndTimeframeOrderByStartTimeDesc(String symbol, String timeframe);
    
    /**
     * Find candles within time range
     */
    @Query("SELECT c FROM Candle c WHERE c.symbol = :symbol AND c.timeframe = :timeframe " +
           "AND c.startTime >= :startTime AND c.startTime <= :endTime ORDER BY c.startTime ASC")
    List<Candle> findCandlesInTimeRange(@Param("symbol") String symbol, 
                                       @Param("timeframe") String timeframe,
                                       @Param("startTime") LocalDateTime startTime,
                                       @Param("endTime") LocalDateTime endTime);
    
    /**
     * Find latest N candles for a symbol
     */
    List<Candle> findTop500BySymbolAndTimeframeOrderByStartTimeDesc(String symbol, String timeframe);
}
