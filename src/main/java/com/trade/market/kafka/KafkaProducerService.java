package com.trade.market.kafka;

import com.trade.market.dto.IndicatorResultDto;
import com.trade.market.dto.PatternMessage;
import com.trade.market.entity.Candle;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class KafkaProducerService {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${market.kafka.topics.candle:market.candle}")
    private String candleTopic;

    @Value("${market.kafka.topics.indicator:indicator.updated}")
    private String indicatorTopic;

    @Value("${market.kafka.topics.pattern:pattern.detected}")
    private String patternTopic;

    public void publishCandle(Candle candle) {
        kafkaTemplate.send(candleTopic, candle.getSymbol(), candle);
        log.debug("Published candle to {} for {}", candleTopic, candle.getSymbol());
    }

    public void publishIndicator(String symbol, IndicatorResultDto indicatorResultDto) {
        kafkaTemplate.send(indicatorTopic, symbol, indicatorResultDto);
        log.debug("Published indicator update to {} for {}", indicatorTopic, symbol);
    }

    public void publishPattern(PatternMessage patternMessage) {
        kafkaTemplate.send(patternTopic, patternMessage.getSymbol(), patternMessage);
        log.debug("Published pattern {} for {} to {}", patternMessage.getPatternName(), patternMessage.getSymbol(), patternTopic);
    }
}
