package com.trade.market.controller;

import com.trade.market.job.IndicatorProcessorService;
import com.trade.market.service.ProcessingRunService;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/api/backtest")
@RequiredArgsConstructor
public class BacktestController {

    private final IndicatorProcessorService indicatorProcessorService;
    private final ProcessingRunService processingRunService;

    /**
     * Trigger a scheduled backtest asynchronously. Optional JSON body fields:
     * - startDatetime: ISO-8601 instant (e.g. 2023-01-02T15:04:05Z)
     * - endDatetime: ISO-8601 instant
     *
     * Example curl:
     * curl -X POST http://localhost:8080/api/backtest/run -H "Content-Type: application/json" \
     *   -d '{"startDatetime":"2023-01-01T00:00:00Z","endDatetime":"2023-01-02T00:00:00Z"}'
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> runScheduledBacktest(
            @RequestBody(required = false) BacktestRequest req) {
        try {
            String runId = "scheduled-backtest-" + System.currentTimeMillis();
            processingRunService.createRun(runId, "BACKTEST", convertToLocalDateTime(req == null ? null : req.getStartDatetime()), convertToLocalDateTime(req == null ? null : req.getEndDatetime()));
            indicatorProcessorService.scheduleBacktestRun(runId, req == null ? null : req.getStartDatetime(), req == null ? null : req.getEndDatetime());
            return ResponseEntity.ok(Collections.singletonMap("runId", runId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    private java.time.LocalDateTime convertToLocalDateTime(String isoInstant) {
        if (isoInstant == null || isoInstant.isBlank()) {
            return null;
        }
        return java.time.LocalDateTime.ofInstant(java.time.Instant.parse(isoInstant), java.time.ZoneId.systemDefault());
    }

    @Data
    public static class BacktestRequest {
        private String startDatetime;
        private String endDatetime;
    }
}
