package com.trade.market.backtest;

import com.trade.market.entity.Candle;
import com.trade.market.job.IndicatorProcessorService;
import com.trade.market.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class BacktestCommandLineRunner implements CommandLineRunner {

    private static final String BACKTEST_ARG = "--backtest";
    private static final String SYMBOL_ARG_PREFIX = "--symbol=";
    private static final String TIMEFRAME_ARG_PREFIX = "--timeframe=";
    private static final String RUN_ID_ARG_PREFIX = "--runId=";
    private static final String DEFAULT_TIMEFRAME = "ONE_MINUTE";
    private static final String DEFAULT_SYMBOL = "NIFTY";

    private final IndicatorProcessorService indicatorProcessorService;
    private final CandleRepository candleRepository;

    @Override
    public void run(String... args) {
        if (!Arrays.asList(args).contains(BACKTEST_ARG)) {
            return;
        }

        String symbol = findArg(args, SYMBOL_ARG_PREFIX).orElse(DEFAULT_SYMBOL);
        String timeframe = findArg(args, TIMEFRAME_ARG_PREFIX).orElse(DEFAULT_TIMEFRAME);
        String runId = findArg(args, RUN_ID_ARG_PREFIX).orElse("backtest-" + Instant.now().toEpochMilli());

        log.info("Backtest startup requested: symbol={}, timeframe={}, runId={}", symbol, timeframe, runId);

        List<Candle> history = candleRepository.findBySymbolAndTimeframeOrderByCandleTimeDesc(symbol, timeframe);
        if (history.isEmpty()) {
            log.warn("No historical candles found for symbol={} timeframe={}. Backtest aborted.", symbol, timeframe);
            return;
        }

        Collections.reverse(history);
        indicatorProcessorService.runBacktest(symbol, history, runId);
        log.info("Backtest finished for symbol={} timeframe={} runId={}", symbol, timeframe, runId);
    }

    private Optional<String> findArg(String[] args, String prefix) {
        return Arrays.stream(args)
                .filter(arg -> arg.startsWith(prefix))
                .map(arg -> arg.substring(prefix.length()))
                .findFirst();
    }
}
