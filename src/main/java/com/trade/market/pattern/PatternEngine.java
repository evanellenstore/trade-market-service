package com.trade.market.pattern;
import java.util.List;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;
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
    private static final double FALLBACK_MOVE_THRESHOLD = 0.5; // % move required to trigger fallback

    /**
     * Constructs the PatternEngine with auto-discovered detectors.
     * Spring will inject all beans implementing PatternDetector.
     *
     * @param detectors list of detected pattern detector implementations
     */
    public PatternEngine(List<PatternDetector> detectors) {
        this.detectors = detectors != null ? new java.util.ArrayList<>(detectors) : new java.util.ArrayList<>();
        this.detectors.sort((a, b) -> Integer.compare(b.getPriority(), a.getPriority()));
        log.info("PatternEngine initialized with {} detectors", this.detectors.size());
    }

    /**
     * Detects chart patterns from candlestick data.
     * Returns the first confirmed pattern with sufficient confidence, or NONE.
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

        for (PatternDetector detector : detectors) {
            try {
                log.debug("Running detector {} for symbol {} on {} candles", detector.pattern(), symbol, candles.size());
                PatternResult result = detector.detectWithResult(candles);

                if (result != null && result.isPatternDetected()) {
                    // Enforce the documented confidence gate instead of just logging it.
                    if (result.getConfidence() < MINIMUM_CONFIDENCE) {
                        log.info("Detector {} produced pattern for {} with confidence {}% below minimum {}% - discarding",
                                detector.pattern(), symbol, result.getConfidence(), MINIMUM_CONFIDENCE);
                        continue; // try the next detector instead of returning a weak/garbage result
                    }

                    log.info("Pattern detected for {}: {} (confidence: {}%, breakout: {})",
                            symbol, result.getPattern().getDisplayName(), result.getConfidence(),
                            String.format("%.2f", result.getBreakoutPrice()));

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

        // Guard against div-by-zero / bad data instead of propagating NaN/Infinity.
        if (previous.getClose() <= 0) {
            log.warn("Invalid previous close ({}) for {}, skipping fallback pattern", previous.getClose(), symbol);
            return PatternResult.none();
        }

        double changePercent = ((last.getClose() - previous.getClose()) / previous.getClose()) * 100.0;

        if (changePercent >= FALLBACK_MOVE_THRESHOLD) {
            PatternResult result = PatternResult.builder()
                    .pattern(ChartPattern.RESISTANCE_BREAKOUT)
                    .patternDetected(true) // <-- was missing; caused isPatternDetected() to always be false
                    .confidence(Math.min(90, 60 + (int) Math.round(Math.abs(changePercent) * 2)))
                    .breakoutPrice(last.getClose())
                    .target(last.getClose() + (last.getHigh() - last.getLow()) * 1.5)
                    .stopLoss(previous.getLow())
                    .description("Fallback bullish breakout based on recent price movement")
                    .direction("BULLISH")
                    .patternLength(candles.size())
                    .detectionTime(System.currentTimeMillis())
                    .build();
            log.info("Fallback breakout pattern produced for {} with {}% move", symbol, String.format("%.2f", changePercent));
            return result;
        }

        if (changePercent <= -FALLBACK_MOVE_THRESHOLD) {
            PatternResult result = PatternResult.builder()
                    .pattern(ChartPattern.SUPPORT_BREAKDOWN)
                    .patternDetected(true) // <-- was missing; caused isPatternDetected() to always be false
                    .confidence(Math.min(90, 60 + (int) Math.round(Math.abs(changePercent) * 2)))
                    .breakoutPrice(last.getClose())
                    .target(last.getClose() - (last.getHigh() - last.getLow()) * 1.5)
                    .stopLoss(previous.getHigh())
                    .description("Fallback bearish breakdown based on recent price movement")
                    .direction("BEARISH")
                    .patternLength(candles.size())
                    .detectionTime(System.currentTimeMillis())
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
        // Detectors are already sorted in the constructor; return a defensive copy
        // without re-sorting to avoid duplicating that work on every call.
        return List.copyOf(detectors);
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