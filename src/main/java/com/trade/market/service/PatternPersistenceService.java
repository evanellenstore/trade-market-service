package com.trade.market.service;

import com.trade.market.entity.MarketPattern;
import com.trade.market.pattern.ChartPattern;
import com.trade.market.pattern.PatternResult;
import com.trade.market.repository.MarketPatternRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class PatternPersistenceService {

    private final MarketPatternRepository marketPatternRepository;

    public void save(String symbol,
                     String symbolToken,
                     String timeframe,
                     String runId,
                     String origin,
                     LocalDateTime candleTime,
                     PatternResult patternResult) {
        MarketPattern.MarketPatternBuilder builder = MarketPattern.builder()
                .symbol(symbol)
                .symbolToken(symbolToken)
                .timeframe(timeframe)
                .runId(runId)
                .origin(origin)
                .candleTime(candleTime);

        if (patternResult != null && patternResult.isPatternDetected()) {
            ChartPattern chartPattern = patternResult.getPattern();
            if (chartPattern != null) {
                switch (chartPattern) {
                    case DOUBLE_TOP -> builder.doubleTop(true);
                    case DOUBLE_BOTTOM -> builder.doubleBottom(true);
                    case HEAD_AND_SHOULDERS -> builder.headAndShoulders(true);
                    case INVERSE_HEAD_AND_SHOULDERS -> builder.inverseHeadAndShoulders(true);
                    case ASCENDING_TRIANGLE -> builder.ascendingTriangle(true);
                    case DESCENDING_TRIANGLE -> builder.descendingTriangle(true);
                    case SYMMETRICAL_TRIANGLE -> builder.symmetricalTriangle(true);
                    case BULL_FLAG -> builder.bullFlag(true);
                    case BEAR_FLAG -> builder.bearFlag(true);
                    case CUP_AND_HANDLE -> builder.cupAndHandle(true);
                    case RISING_WEDGE -> builder.risingWedge(true);
                    case FALLING_WEDGE -> builder.fallingWedge(true);
                    case RECTANGLE -> builder.rectangle(true);
                    case RESISTANCE_BREAKOUT -> builder.breakout(true);
                    case SUPPORT_BREAKDOWN -> builder.breakdown(true);
                    default -> log.debug("Unhandled chart pattern type {}", chartPattern);
                }
            }
        }

        try {
            MarketPattern persisted = builder.build();
            marketPatternRepository.save(persisted);
            log.info("Persisted market pattern for symbol={} timeframe={} candleTime={} pattern={} detected={}",
                    symbol, timeframe, candleTime, patternResult != null && patternResult.getPattern() != null ? patternResult.getPattern().name() : "NONE",
                    patternResult != null && patternResult.isPatternDetected());
        } catch (Exception e) {
            log.error("Failed to persist market pattern for {} {}", symbol, timeframe, e);
        }
    }
}
