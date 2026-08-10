package com.trade.market.controller;

import com.trade.market.dto.BacktestRequest;
import com.trade.market.entity.Candle;
import com.trade.market.entity.MarketIndicator;
import com.trade.market.entity.MarketPattern;
import com.trade.market.job.IndicatorProcessorService;
import com.trade.market.repository.CandleRepository;
import com.trade.market.repository.MarketIndicatorRepository;
import com.trade.market.repository.MarketPatternRepository;
import com.trade.market.util.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/market")
@RequiredArgsConstructor
@Slf4j
public class BacktestController {

    private final IndicatorProcessorService indicatorProcessorService;
    private final CandleRepository candleRepository;
    private final MarketIndicatorRepository marketIndicatorRepository;
    private final MarketPatternRepository marketPatternRepository;

    @PostMapping("/backtest")
    public ResponseEntity<ApiResponse<Object>> runBacktest(@RequestBody BacktestRequest request) {
        String symbol = request.getSymbol();
        String timeframe = request.getTimeframe() == null ? "ONE_MINUTE" : request.getTimeframe();
        String runId = request.getRunId() == null ? "backtest-" + System.currentTimeMillis() : request.getRunId();

        if (symbol == null || symbol.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Symbol is required"));
        }

        List<Candle> history;
        if (request.getStartTime() != null || request.getEndTime() != null) {
            LocalDateTime startTime;
            LocalDateTime endTime;
            try {
                startTime = request.getStartTime() != null
                        ? LocalDateTime.parse(request.getStartTime())
                        : LocalDateTime.of(1970, 1, 1, 0, 0);
                endTime = request.getEndTime() != null
                        ? LocalDateTime.parse(request.getEndTime())
                        : LocalDateTime.now();
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body(ApiResponse.error("Invalid startTime or endTime. Use ISO local date-time format like 2026-08-08T12:00"));
            }

            if (endTime.isBefore(startTime)) {
                return ResponseEntity.badRequest().body(ApiResponse.error("endTime must be after startTime"));
            }

            history = candleRepository.findCandlesInTimeRange(symbol, timeframe, startTime, endTime);
        } else {
            history = candleRepository.findBySymbolAndTimeframeOrderByCandleTimeDesc(symbol, timeframe);
            Collections.reverse(history);
        }

        if (history.isEmpty()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("No historical candles found for symbol=" + symbol + " timeframe=" + timeframe));
        }

        indicatorProcessorService.runBacktest(symbol, history, runId);
        log.info("Backtest started for symbol={} timeframe={} runId={} startTime={} endTime={}",
                symbol, timeframe, runId, request.getStartTime(), request.getEndTime());

        return ResponseEntity.ok(ApiResponse.success(Collections.singletonMap("runId", runId), "Backtest started"));
    }

    @GetMapping("/backtest/report")
    public ResponseEntity<ApiResponse<Object>> getBacktestReport(
            @RequestParam String symbol,
            @RequestParam(defaultValue = "ONE_MINUTE") String timeframe,
            @RequestParam(required = false) String runId,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        if (symbol == null || symbol.isBlank()) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Symbol is required"));
        }

        LocalDateTime start = null;
        LocalDateTime end = null;
        try {
            if (startTime != null) {
                start = LocalDateTime.parse(startTime);
            }
            if (endTime != null) {
                end = LocalDateTime.parse(endTime);
            }
        } catch (DateTimeParseException e) {
            return ResponseEntity.badRequest().body(ApiResponse.error("Invalid startTime or endTime. Use ISO local date-time format like 2026-08-08T12:00"));
        }

        if (start != null && end != null && end.isBefore(start)) {
            return ResponseEntity.badRequest().body(ApiResponse.error("endTime must be after startTime"));
        }

        List<MarketIndicator> indicators;
        List<MarketPattern> patterns;

        if (runId != null && !runId.isBlank()) {
            if (start != null || end != null) {
                LocalDateTime actualStart = start != null ? start : LocalDateTime.of(1970, 1, 1, 0, 0);
                LocalDateTime actualEnd = end != null ? end : LocalDateTime.now();
                indicators = marketIndicatorRepository.findBySymbolAndTimeframeAndRunIdAndCandleTimeBetweenOrderByCandleTimeAsc(symbol, timeframe, runId, actualStart, actualEnd);
                patterns = marketPatternRepository.findBySymbolAndTimeframeAndRunIdAndCandleTimeBetweenOrderByCandleTimeAsc(symbol, timeframe, runId, actualStart, actualEnd);
            } else {
                indicators = marketIndicatorRepository.findBySymbolAndTimeframeAndRunIdOrderByCandleTimeAsc(symbol, timeframe, runId);
                patterns = marketPatternRepository.findBySymbolAndTimeframeAndRunIdOrderByCandleTimeAsc(symbol, timeframe, runId);
            }
        } else if (start != null || end != null) {
            LocalDateTime actualStart = start != null ? start : LocalDateTime.of(1970, 1, 1, 0, 0);
            LocalDateTime actualEnd = end != null ? end : LocalDateTime.now();
            indicators = marketIndicatorRepository.findBySymbolAndTimeframeAndCandleTimeBetweenOrderByCandleTimeAsc(symbol, timeframe, actualStart, actualEnd);
            patterns = marketPatternRepository.findBySymbolAndTimeframeAndCandleTimeBetweenOrderByCandleTimeAsc(symbol, timeframe, actualStart, actualEnd);
        } else {
            indicators = marketIndicatorRepository.findBySymbolAndTimeframeOrderByCandleTimeAsc(symbol, timeframe);
            patterns = marketPatternRepository.findBySymbolAndTimeframeOrderByCandleTimeAsc(symbol, timeframe);
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of("indicators", indicators, "patterns", patterns), "Backtest report retrieved"));
    }

    @GetMapping("/symbols")
    public ResponseEntity<ApiResponse<List<String>>> getSymbols() {
        List<String> symbols = candleRepository.findDistinctSymbols();
        symbols.sort(String::compareToIgnoreCase);
        return ResponseEntity.ok(ApiResponse.success(symbols, "Symbols retrieved"));
    }

    @PostMapping("/run-live")
    public ResponseEntity<ApiResponse<Object>> runLiveIndicators() {
        indicatorProcessorService.processLatestIndicatorsLive();
        log.info("Manual live indicator processing requested");
        return ResponseEntity.ok(ApiResponse.success(Collections.singletonMap("status", "started"), "Live indicator processing started"));
    }
}
