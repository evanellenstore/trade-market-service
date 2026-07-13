package com.trade.market.service;

import com.trade.market.dto.IndicatorResultDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IndicatorServiceTest {

    private final IndicatorService indicatorService = new IndicatorService();

    @Test
    void shouldCalculateCoreIndicatorsForClosePrices() {
        List<Double> closes = List.of(10.0, 11.0, 12.0, 11.5, 13.0, 14.0, 15.0, 16.0, 15.5, 16.5);

        IndicatorResultDto result = indicatorService.calculateIndicators("NSE:RELIANCE", "ONE_MINUTE", closes);

        assertEquals("NSE:RELIANCE", result.getSymbol());
        assertEquals("ONE_MINUTE", result.getTimeframe());
        assertTrue(result.getEma20() > 0.0);
        assertTrue(result.getEma50() > 0.0);
        assertTrue(result.getEma200() > 0.0);
        assertTrue(result.getRsi14() >= 0.0);
        assertTrue(result.getMacd() != 0.0 || result.getSignal() != 0.0 || result.getHistogram() != 0.0);
    }
}
