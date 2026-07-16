package com.trade.market.repository;

import com.trade.market.entity.MarketIndicator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketIndicatorRepository extends JpaRepository<MarketIndicator, Long> {
}
