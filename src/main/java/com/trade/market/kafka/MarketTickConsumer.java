package com.trade.market.kafka;

import com.trade.market.dto.TickDto;
import com.trade.market.service.TickProcessorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class MarketTickConsumer {

    private final TickProcessorService tickProcessorService;

    @KafkaListener(topics = "market.tick", groupId = "${spring.kafka.consumer.group-id}")
    public void consume(TickDto tick) {
        try {
            log.debug("Received tick: {} price: {}", tick.getSymbol(), tick.getPrice());
            tickProcessorService.processTick(tick);
        } catch (Exception e) {
            log.error("Error processing tick: {}", tick, e);
        }
    }
}