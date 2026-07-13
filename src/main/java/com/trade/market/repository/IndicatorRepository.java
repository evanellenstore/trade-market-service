package com.trade.market.repository;

import com.trade.market.entity.Indicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface IndicatorRepository extends JpaRepository<Indicator, Long> {
    Optional<Indicator> findTopBySymbolAndTimeframeOrderByCreatedAtDesc(String symbol, String timeframe);
}
