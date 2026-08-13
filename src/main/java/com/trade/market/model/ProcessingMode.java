package com.trade.market.model;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class ProcessingMode {

    private final boolean live;
    private final boolean publish;
    private final boolean persist;
    private final String runId;

    public static ProcessingMode live() {
        return new ProcessingMode(true, true, true, null);
    }

    /**
     * Creates a live ProcessingMode with an associated runId and persistence/publish options.
     * @param runId optional run id associated with the live run
     * @param persist whether to persist indicator/pattern results
     * @param publish whether to publish indicator/pattern results
     * @return configured ProcessingMode for live runs
     */
    public static ProcessingMode live(String runId, boolean persist, boolean publish) {
        return new ProcessingMode(true, publish, persist, runId);
    }

    public static ProcessingMode backtest(String runId, boolean persist, boolean publish) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must be provided for backtest mode");
        }
        return new ProcessingMode(false, publish, persist, runId);
    }

    public boolean isLive() {
        return live;
    }
}
