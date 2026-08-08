package com.trade.market.pattern;

import com.trade.market.pattern.detector.DoubleTopDetector;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PatternEngineTest {

    @Mock
    private DoubleTopDetector detector;

    @Test
    void shouldReturnDetectedPatternResultEvenWhenConfidenceIsBelowMinimum() {
        Candle candle = Candle.builder()
                .symbol("BTCUSDT")
                .open(100.0)
                .high(101.0)
                .low(99.0)
                .close(100.5)
                .volume(1000L)
                .startTime(System.currentTimeMillis())
                .build();

        PatternResult expected = PatternResult.builder()
                .pattern(ChartPattern.DOUBLE_TOP)
                .confidence(40)
                .description("test")
                .build();

        when(detector.detectWithResult(List.of(candle))).thenReturn(expected);
        when(detector.pattern()).thenReturn(ChartPattern.DOUBLE_TOP);
        when(detector.getPriority()).thenReturn(100);

        PatternEngine engine = new PatternEngine(List.of(detector));
        PatternResult result = engine.detectPattern("BTCUSDT", List.of(candle));

        assertTrue(result.isPatternDetected());
    }
}
