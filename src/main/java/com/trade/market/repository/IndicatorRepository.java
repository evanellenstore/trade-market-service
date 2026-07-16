package com.trade.market.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.trade.market.entity.MarketIndicator;

@Repository
public interface IndicatorRepository extends JpaRepository<MarketIndicator, Long> {
    Optional<MarketIndicator> findTopBySymbolAndTimeframeOrderByCreatedAtDesc(String symbol, String timeframe);
}
