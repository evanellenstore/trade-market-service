package com.trade.market.service;

import com.trade.market.dto.IndicatorResultDto;
import com.trade.market.entity.MarketIndicator;
import com.trade.market.repository.MarketIndicatorRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class IndicatorPersistenceService {

    private final MarketIndicatorRepository marketIndicatorRepository;

    public void save(IndicatorResultDto dto) {
        MarketIndicator indicator = MarketIndicator.builder()
                .symbol(dto.getSymbol())
                .symbolToken(dto.getSymbolToken())
                .timeframe(dto.getTimeframe())
                .runId(dto.getRunId())
                .origin(dto.getOrigin())
                .trend_ema(dto.getEma())
                .trend_ema20(dto.getEma20())
                .trend_ema50(dto.getEma50())
                .trend_ema100(dto.getEma100())
                .trend_ema200(dto.getEma200())
                .trend_adx(dto.getAdx())
                .trend_plusDi(dto.getPlusDi())
                .trend_minusDi(dto.getMinusDi())
                .trend_supertrend(dto.getSupertrend())

                .momentum_rsi14(dto.getRsi14())
                .momentum_macd(dto.getMacd())
                .momentum_macdSignal(dto.getMacdSignal())
                .momentum_macdHistogram(dto.getMacdHistogram())
                .momentum_stochasticK(dto.getStochasticK())
                .momentum_stochasticD(dto.getStochasticD())
                .momentum_cci(dto.getCci())
                .momentum_roc(dto.getRoc())

                .volume_vwap(dto.getVwap())
                .volume_obv(dto.getObv())
                .volume_mfi(dto.getMfi())
                .volume_cmf(dto.getCmf())

                .volatility_atr(dto.getAtr())
                .volatility_bbUpper(dto.getBbUpper())
                .volatility_bbMiddle(dto.getBbMiddle())
                .volatility_bbLower(dto.getBbLower())
                .volatility_bbWidth(dto.getBbWidth())
                .volatility_percentB(dto.getPercentB())
                
                .pivot(dto.getPivot())
                .support1(dto.getSupport1())
                .support2(dto.getSupport2())
                .resistance1(dto.getResistance1())
                .resistance2(dto.getResistance2())
                .candleTime(dto.getCandleTime())
                .build();
        marketIndicatorRepository.save(indicator);
        log.debug("Persisted market indicators for {} {}", dto.getSymbol(), dto.getTimeframe());
    }
}
