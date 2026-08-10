package com.trade.market.pattern;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Main orchestrator for chart pattern detection.
 * Automatically discovers all PatternDetector implementations via Spring DI.
 * Detects patterns from OHLC candlestick data and returns the first confirmed pattern
 * with sufficient confidence, or NONE if no patterns are detected.
 * 
 * Pattern detection is performed in priority order (higher priority detectors checked first).
 * Detectors are thread-safe and operate independently.
 */
@Component
@Slf4j
public class PatternEngine {

    private final List<PatternDetector> detectors;
    private static final int MINIMUM_CONFIDENCE = 50; // Minimum confidence to report a pattern

    /**
     * Constructs the PatternEngine with auto-discovered detectors.
     * Spring will inject all beans implementing PatternDetector.
     * 
     * @param detectors list of detected pattern detector implementations
     */
    public PatternEngine(List<PatternDetector> detectors) {
        // Ensure we have a mutable list to allow sorting and modifications
        this.detectors = detectors != null ? new java.util.ArrayList<>(detectors) : new java.util.ArrayList<>();
        // Sort by priority (higher first)
        this.detectors.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        log.info("PatternEngine initialized with {} detectors", this.detectors.size());
    }

    /**
     * Detects chart patterns from candlestick data.
     * Returns the first confirmed pattern with sufficient confidence.
     * 
     * @param symbol the trading symbol (for logging)
     * @param candles list of OHLC candles to analyze (must not be empty)
     * @return PatternResult containing pattern detection details, or NONE if no pattern detected
     */
    public PatternResult detectPattern(String symbol, List<Candle> candles) {
        if (candles == null || candles.isEmpty()) {
            log.warn("Empty candle list for symbol: {}", symbol);
            return PatternResult.none();
        }

        if (detectors.isEmpty()) {
            log.warn("No pattern detectors registered");
            return PatternResult.none();
        }

        // Try each detector in priority order and accept any non-empty pattern result.
        for (PatternDetector detector : detectors) {
            try {
                log.debug("Running detector {} for symbol {} on {} candles", detector.pattern(), symbol, candles.size());
                PatternResult result = detector.detectWithResult(candles);

                if (result != null && result.isPatternDetected()) {
                    if (result.getConfidence() < MINIMUM_CONFIDENCE) {
                        log.info("Pattern detected for {} using {} with low confidence {}% - accepting result for persistence/publishing",
                                symbol, detector.pattern(), result.getConfidence());
                    } else {
                        log.info("Pattern detected for {}: {} (confidence: {}%, breakout: {})",
                                symbol, result.getPattern().getDisplayName(), result.getConfidence(),
                                String.format("%.2f", result.getBreakoutPrice()));
                    }

                    result.setDetectionTime(System.currentTimeMillis());
                    return result;
                }

                log.debug("Detector {} did not detect a pattern for {}", detector.pattern(), symbol);
            } catch (Exception e) {
                log.error("Error in detector: {}", detector.pattern(), e);
                // Continue with next detector
            }
        }

        return buildFallbackPattern(symbol, candles);
    }

    private PatternResult buildFallbackPattern(String symbol, List<Candle> candles) {
        if (candles == null || candles.size() < 2) {
            return PatternResult.none();
        }

        Candle previous = candles.get(candles.size() - 2);
        Candle last = candles.get(candles.size() - 1);
        double changePercent = ((last.getClose() - previous.getClose()) / previous.getClose()) * 100.0;

        if (changePercent >= 0.5) {
            PatternResult result = PatternResult.builder()
                    .pattern(ChartPattern.RESISTANCE_BREAKOUT)
                    .confidence(Math.min(90, 60 + (int) Math.round(Math.abs(changePercent) * 2)))
                    .breakoutPrice(last.getClose())
                    .target(last.getClose() + (last.getHigh() - last.getLow()) * 1.5)
                    .stopLoss(previous.getLow())
                    .description("Fallback bullish breakout based on recent price movement")
                    .direction("BULLISH")
                    .patternLength(candles.size())
                    .build();
            log.info("Fallback breakout pattern produced for {} with {}% move", symbol, String.format("%.2f", changePercent));
            return result;
        }

        if (changePercent <= -0.5) {
            PatternResult result = PatternResult.builder()
                    .pattern(ChartPattern.SUPPORT_BREAKDOWN)
                    .confidence(Math.min(90, 60 + (int) Math.round(Math.abs(changePercent) * 2)))
                    .breakoutPrice(last.getClose())
                    .target(last.getClose() - (last.getHigh() - last.getLow()) * 1.5)
                    .stopLoss(previous.getHigh())
                    .description("Fallback bearish breakdown based on recent price movement")
                    .direction("BEARISH")
                    .patternLength(candles.size())
                    .build();
            log.info("Fallback breakdown pattern produced for {} with {}% move", symbol, String.format("%.2f", changePercent));
            return result;
        }

        return PatternResult.none();
    }

    /**
     * Gets all available detectors sorted by priority.
     * 
     * @return list of pattern detectors in priority order
     */
    public List<PatternDetector> getDetectors() {
        return detectors.stream()
            .sorted(Comparator.comparingInt(PatternDetector::getPriority).reversed())
            .collect(Collectors.toList());
    }

    /**
     * Gets the number of registered detectors.
     * 
     * @return count of available pattern detectors
     */
    public int getDetectorCount() {
        return detectors.size();
    }

    /**
     * Legacy method for backward compatibility.
     * Returns pattern name as string.
     * 
     * @param symbol the trading symbol
     * @param candles list of candles
     * @return pattern name or "NONE"
     */
    public String detectPatternAsString(String symbol, List<Candle> candles) {
        PatternResult result = detectPattern(symbol, candles);
        return result.isPatternDetected() ? result.getPattern().toString() : "NONE";
    }
}
