package com.trade.market.job;

import com.trade.market.datasource.LiveMarketDataSource;
import com.trade.market.dto.IndicatorResultDto;
import com.trade.market.indicator.BarSeriesManager;
import com.trade.market.kafka.KafkaProducerService;
import com.trade.market.pattern.PatternEngine;
import com.trade.market.pattern.PatternResult;
import com.trade.market.repository.CandleRepository;
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
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IndicatorProcessorServiceTest {

    @Mock
    private CandleRepository candleRepository;

    @Mock
    private LiveMarketDataSource liveMarketDataSource;

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
    void processLatestIndicatorsShouldPassCandlesInChronologicalOrderToPatternEngine() {
        String symbol = "BTCUSDT";
        com.trade.market.entity.Candle newest = com.trade.market.entity.Candle.builder()
                .symbol(symbol)
                .symbolToken("BTC")
                .timeframe("ONE_MINUTE")
                .candleTime(LocalDateTime.of(2024, 1, 1, 10, 2))
                .open(100.0)
                .high(101.0)
                .low(99.0)
                .close(100.5)
                .volume(1000)
                .build();

        com.trade.market.entity.Candle oldest = com.trade.market.entity.Candle.builder()
                .symbol(symbol)
                .symbolToken("BTC")
                .timeframe("ONE_MINUTE")
                .candleTime(LocalDateTime.of(2024, 1, 1, 10, 1))
                .open(99.0)
                .high(100.0)
                .low(98.0)
                .close(99.5)
                .volume(900)
                .build();

        when(liveMarketDataSource.getSymbols()).thenReturn(List.of(symbol));
        when(liveMarketDataSource.getCandles(symbol, "ONE_MINUTE", 500)).thenReturn(List.of(newest, oldest));
        when(barSeriesManager.getSeriesByKey(symbol, "ONE_MINUTE")).thenReturn(null);
        when(indicatorService.calculateIndicatorsBySeriesKey(eq(symbol), eq("ONE_MINUTE"), eq(symbol), eq("BTC"), eq(newest.getCandleTime()), anyList()))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    List<Double> closes = invocation.getArgument(5);
                    assertEquals(List.of(99.5, 100.5), closes);
                    return mock(IndicatorResultDto.class);
                });
        when(patternEngine.detectPattern(anyString(), anyList())).thenReturn(PatternResult.none());

        indicatorProcessorService.processLatestIndicatorsLive();

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<com.trade.market.pattern.Candle>> captor = ArgumentCaptor.forClass((Class<List<com.trade.market.pattern.Candle>>) (Class<?>) List.class);
        verify(patternEngine).detectPattern(eq(symbol), captor.capture());

        List<com.trade.market.pattern.Candle> patternCandles = captor.getValue();
        assertEquals(2, patternCandles.size());
        assertEquals(oldest.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                patternCandles.get(0).getStartTime());
        assertEquals(newest.getStartTime().atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli(),
                patternCandles.get(1).getStartTime());
    }
}
