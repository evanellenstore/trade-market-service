package com.trade.market.repository;

import com.trade.market.entity.ProcessingRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProcessingRunRepository extends JpaRepository<ProcessingRun, Long> {
    Optional<ProcessingRun> findByRunId(String runId);
}
