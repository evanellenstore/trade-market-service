package com.trade.market.repository;

import com.trade.market.entity.MarketPattern;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface MarketPatternRepository extends JpaRepository<MarketPattern, Long> {
}
