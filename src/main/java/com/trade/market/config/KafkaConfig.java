package com.trade.market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import com.trade.market.dto.TickDto;

@Configuration
public class KafkaConfig {

    @Bean
    JsonDeserializer<TickDto> jsonDeserializer() {

        JsonDeserializer<TickDto> deserializer =
                new JsonDeserializer<>(TickDto.class);

        deserializer.addTrustedPackages("*");

        return deserializer;

    }

}