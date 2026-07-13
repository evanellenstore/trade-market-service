package com.trade.market.service;

import com.trade.market.dto.IndicatorResultDto;
import com.trade.market.entity.Indicator;
import com.trade.market.repository.IndicatorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndicatorPersistenceService {

    private final IndicatorRepository indicatorRepository;

    public void save(IndicatorResultDto dto) {
        Indicator indicator = Indicator.builder()
                .symbol(dto.getSymbol())
                .timeframe(dto.getTimeframe())
                .ema20(dto.getEma20())
                .ema50(dto.getEma50())
                .ema200(dto.getEma200())
                .rsi14(dto.getRsi14())
                .macd(dto.getMacd())
                .signal(dto.getSignal())
                .histogram(dto.getHistogram())
                .atr(dto.getAtr())
                .adx(dto.getAdx())
                .vwap(dto.getVwap())
                .supertrend(dto.getSupertrend())
                .bbUpper(dto.getBbUpper())
                .bbMiddle(dto.getBbMiddle())
                .bbLower(dto.getBbLower())
                .pivot(dto.getPivot())
                .support1(dto.getSupport1())
                .support2(dto.getSupport2())
                .resistance1(dto.getResistance1())
                .resistance2(dto.getResistance2())
                .build();
        indicatorRepository.save(indicator);
        log.debug("Persisted indicators for {} {}", dto.getSymbol(), dto.getTimeframe());
    }
}
