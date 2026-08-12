package com.trade.market.service;

import com.trade.market.entity.ProcessingRun;
import com.trade.market.repository.ProcessingRunRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProcessingRunService {

    private final ProcessingRunRepository processingRunRepository;

    public ProcessingRun createRun(String runId, String origin, LocalDateTime startDatetime, LocalDateTime endDatetime) {
        ProcessingRun processingRun = ProcessingRun.builder()
                .runId(runId)
                .origin(origin)
                .status("STARTED")
                .startDatetime(startDatetime)
                .endDatetime(endDatetime)
                .startedAt(LocalDateTime.now())
                .build();
        return processingRunRepository.save(processingRun);
    }

    public Optional<ProcessingRun> findByRunId(String runId) {
        return processingRunRepository.findByRunId(runId);
    }

    public ProcessingRun markCompleted(String runId) {
        Optional<ProcessingRun> optional = findByRunId(runId);
        return optional.map(run -> {
            run.setStatus("COMPLETED");
            run.setCompletedAt(LocalDateTime.now());
            return processingRunRepository.save(run);
        }).orElse(null);
    }

    public ProcessingRun markFailed(String runId) {
        Optional<ProcessingRun> optional = findByRunId(runId);
        return optional.map(run -> {
            run.setStatus("FAILED");
            run.setCompletedAt(LocalDateTime.now());
            return processingRunRepository.save(run);
        }).orElse(null);
    }
}
