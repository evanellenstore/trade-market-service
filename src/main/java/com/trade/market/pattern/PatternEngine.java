package com.trade.market.pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@Slf4j
public class PatternEngine {

    public String detectPattern(String symbol, List<Double> closePrices) {
        if (closePrices == null || closePrices.size() < 3) {
            return "NONE";
        }

        if (isDoubleTop(closePrices)) {
            return "DOUBLE_TOP";
        }
        if (isDoubleBottom(closePrices)) {
            return "DOUBLE_BOTTOM";
        }
        if (isHeadAndShoulders(closePrices)) {
            return "HEAD_AND_SHOULDERS";
        }
        if (isTriangle(closePrices)) {
            return "TRIANGLE";
        }
        return "NONE";
    }

    private boolean isDoubleTop(List<Double> prices) {
        int size = prices.size();
        return size >= 4 && prices.get(size - 1) > prices.get(size - 2) && prices.get(size - 3) > prices.get(size - 2);
    }

    private boolean isDoubleBottom(List<Double> prices) {
        int size = prices.size();
        return size >= 4 && prices.get(size - 1) < prices.get(size - 2) && prices.get(size - 3) < prices.get(size - 2);
    }

    private boolean isHeadAndShoulders(List<Double> prices) {
        int size = prices.size();
        return size >= 5 && prices.get(size - 1) < prices.get(size - 3) && prices.get(size - 2) > prices.get(size - 3);
    }

    private boolean isTriangle(List<Double> prices) {
        int size = prices.size();
        return size >= 5 && Math.abs(prices.get(size - 1) - prices.get(size - 2)) < 0.2;
    }
}
