package com.trade.market.service;
import com.trade.market.dto.CandleDto;
import com.trade.market.dto.IndicatorResultDto;
import com.trade.market.entity.Candle;
import com.trade.market.repository.CandleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class CandleService {
    
    private final CandleRepository candleRepository;
    private final IndicatorService indicatorService;
    
    public List<CandleDto> getCandles(String symbol, String timeframe, int limit) {
        log.debug("Fetching {} candles for {} with timeframe {}", limit, symbol, timeframe);
        
        List<Candle> candles = candleRepository.findBySymbolAndTimeframeOrderByCandleTimeDesc(symbol, timeframe);
        
        return candles.stream()
                .limit(limit)
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }
    
    public IndicatorResultDto getIndicators(String symbol, String timeframe) {
        log.debug("Calculating indicators for {} with timeframe {}", symbol, timeframe);
        
        List<Candle> candles = candleRepository.findTop500BySymbolAndTimeframeOrderByCandleTimeDesc(symbol, timeframe);
        
        if (candles.isEmpty()) {
            return IndicatorResultDto.builder()
                    .symbol(symbol)
                    .timeframe(timeframe)
                    .build();
        }
        
        List<Double> closePrices = candles.stream()
            .map(Candle::getClose)
            .collect(Collectors.toList());

        Candle latest = candles.get(0);
        String symbolToken = latest.getSymbolToken();
        LocalDateTime candleTime = latest.getCandleTime();

        return indicatorService.calculateIndicators(symbol, timeframe, symbolToken, candleTime, closePrices);
    }

    public List<String> getSymbols() {
        log.debug("Loading distinct symbols from candles");
        return candleRepository.findDistinctSymbols();
    }
    
    private CandleDto convertToDto(Candle candle) {
        return CandleDto.builder()
                .id(candle.getId())
                .symbol(candle.getSymbol())
                .exchange(candle.getExchange())
                .timeframe(candle.getTimeframe())
                .open(candle.getOpen())
                .high(candle.getHigh())
                .low(candle.getLow())
                .close(candle.getClose())
                .volume(candle.getVolume())
                .startTime(candle.getStartTime() != null ? candle.getStartTime().toString() : "")
                .endTime(candle.getEndTime() != null ? candle.getEndTime().toString() : "")
                .build();
    }
}
