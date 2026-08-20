package com.trade.market.snapshot.producer;

import com.trade.market.snapshot.dto.MarketSnapshot;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringJUnitConfig(classes = MarketSnapshotProducerIntegrationTest.KafkaTestConfig.class)
@EmbeddedKafka(partitions = 1, topics = "market.snapshot")
class MarketSnapshotProducerIntegrationTest {
    @Autowired
    private MarketSnapshotProducer producer;

    @Autowired
    private ConsumerFactory<String, MarketSnapshot> consumerFactory;

    @Test
    void publishesSnapshotToConfiguredTopic(EmbeddedKafkaBroker broker) {
        Consumer<String, MarketSnapshot> consumer = consumerFactory.createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, "market.snapshot");
        MarketSnapshot snapshot = MarketSnapshot.builder().symbol("VEDL-EQ").price(262.6).build();

        producer.publish(snapshot);

        assertEquals("VEDL-EQ", KafkaTestUtils.getSingleRecord(consumer, "market.snapshot", Duration.ofSeconds(10))
                .value().getSymbol());
        consumer.close();
    }

    @SuppressWarnings("deprecation")
    @TestConfiguration
    static class KafkaTestConfig {
        @Bean
        KafkaTemplate<String, Object> kafkaTemplate(EmbeddedKafkaBroker broker) {
            Map<String, Object> props = KafkaTestUtils.producerProps(broker);
            return new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(props, new StringSerializer(), new JsonSerializer<>()));
        }

        @Bean
        ConsumerFactory<String, MarketSnapshot> consumerFactory(EmbeddedKafkaBroker broker) {
            Map<String, Object> props = new HashMap<>();
            props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, broker.getBrokersAsString());
            props.put(ConsumerConfig.GROUP_ID_CONFIG, "snapshot-test");
            props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "false");
            props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
            JsonDeserializer<MarketSnapshot> valueDeserializer = new JsonDeserializer<>(MarketSnapshot.class);
            valueDeserializer.addTrustedPackages("com.trade.market.snapshot.dto");
            return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), valueDeserializer);
        }

        @Bean
        MarketSnapshotProducer marketSnapshotProducer(KafkaTemplate<String, Object> kafkaTemplate) {
            return new MarketSnapshotProducer(kafkaTemplate);
        }
    }
}