package com.trade.market.job;

import com.trade.market.dto.IndicatorResultDto;
import com.trade.market.entity.Candle;
import com.trade.market.indicator.BarSeriesManager;
import com.trade.market.kafka.KafkaProducerService;
import com.trade.market.model.ProcessingMode;
import com.trade.market.pattern.PatternEngine;
import com.trade.market.pattern.PatternResult;
import com.trade.market.service.IndicatorPersistenceService;
import com.trade.market.service.IndicatorService;
import com.trade.market.service.PatternPersistenceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndicatorProcessorServiceBacktestTest {

    @Mock
    private IndicatorService indicatorService;

    @Mock
    private PatternEngine patternEngine;

    @Mock
    private KafkaProducerService kafkaProducerService;

    @Mock
    private IndicatorPersistenceService indicatorPersistenceService;

    @Mock
    private PatternPersistenceService patternPersistenceService;

    @Mock
    private BarSeriesManager barSeriesManager;

    @InjectMocks
    private IndicatorProcessorService indicatorProcessorService;

    @Test
    void runBacktestShouldProcessHistoryInChronologicalStepsWithoutPublishingWhenPublishDisabled() {
        String symbol = "BTCUSDT";
        String runId = "backtest-123";

        Candle first = Candle.builder()
                .symbol(symbol)
                .symbolToken("BTC")
                .timeframe("ONE_MINUTE")
                .candleTime(LocalDateTime.of(2024, 1, 1, 10, 0))
                .open(100.0)
                .high(101.0)
                .low(99.0)
                .close(100.5)
                .volume(1000)
                .build();

        Candle second = Candle.builder()
                .symbol(symbol)
                .symbolToken("BTC")
                .timeframe("ONE_MINUTE")
                .candleTime(LocalDateTime.of(2024, 1, 1, 10, 1))
                .open(100.5)
                .high(101.5)
                .low(100.0)
                .close(101.0)
                .volume(1100)
                .build();

        when(indicatorService.calculateIndicatorsBySeriesKey(eq(symbol), eq("ONE_MINUTE"), eq(symbol + "::" + runId), eq("BTC"), any(), anyList()))
                .thenReturn(mock(IndicatorResultDto.class));
        when(patternEngine.detectPattern(anyString(), anyList())).thenReturn(PatternResult.none());

        indicatorProcessorService.runBacktest(symbol, List.of(first, second), runId,
                ProcessingMode.backtest(runId, true, false));

        verify(indicatorPersistenceService).save(any(IndicatorResultDto.class));
        //verify(patternPersistenceService).save(eq(symbol), eq(second.getSymbolToken()), eq("ONE_MINUTE"), eq(second.getCandleTime()), any(PatternResult.class));
        verify(kafkaProducerService, never()).publishIndicator(anyString(), any(IndicatorResultDto.class));
        verify(kafkaProducerService, never()).publishPattern(any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.trade.market.pattern.Candle>> candleCaptor = (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
        verify(patternEngine).detectPattern(eq(symbol), candleCaptor.capture());

        List<com.trade.market.pattern.Candle> capturedCandles = candleCaptor.getValue();
        assertEquals(2, capturedCandles.size());
        assertEquals(first.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), capturedCandles.get(0).getStartTime());
        assertEquals(second.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(), capturedCandles.get(1).getStartTime());
    }
}
