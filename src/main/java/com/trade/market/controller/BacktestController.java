package com.trade.market.controller;

import com.trade.market.job.IndicatorProcessorService;
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
@RequestMapping("/market/api/backtest")
@RequiredArgsConstructor
public class BacktestController {

    private final IndicatorProcessorService indicatorProcessorService;

    /**
     * Trigger a scheduled backtest. Optional JSON body fields:
     * - startDatetime: ISO-8601 instant (e.g. 2023-01-02T15:04:05Z)
     * - endDatetime: ISO-8601 instant
     *
     * Example curl:
     * curl -X POST http://localhost:8080/api/backtest/run -H "Content-Type: application/json" \
     *   -d '{"startDatetime":"2023-01-01T00:00:00Z","endDatetime":"2023-01-02T00:00:00Z"}'
     */
    @PostMapping("/run")
    public ResponseEntity<Map<String, String>> runScheduledBacktest(@RequestBody(required = false) BacktestRequest req) {
        try {
            String runId;
            if (req == null || (req.getStartDatetime() == null && req.getEndDatetime() == null)) {
                runId = indicatorProcessorService.runScheduledBacktest();
            } else {
                runId = indicatorProcessorService.runScheduledBacktest(req.getStartDatetime(), req.getEndDatetime());
            }
            return ResponseEntity.ok(Collections.singletonMap("runId", runId));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", e.getMessage()));
        }
    }

    @Data
    public static class BacktestRequest {
        private String startDatetime;
        private String endDatetime;
    }
}
