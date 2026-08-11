package com.trade.market;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableKafka
@ConfigurationPropertiesScan
@EnableFeignClients
public class TradeMarketServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(TradeMarketServiceApplication.class, args);
	}

}
                        