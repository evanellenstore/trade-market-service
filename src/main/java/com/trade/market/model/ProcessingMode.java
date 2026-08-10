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
