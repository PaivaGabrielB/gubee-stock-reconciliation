package com.gubee.stockreconciliation.infrastructure.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    @Value("${gubee.kafka.topics.stock-events-processed:stock.events.processed}")
    private String processedTopic;

    @Bean
    public NewTopic stockEventsProcessedTopic() {
        return TopicBuilder.name(processedTopic)
            .partitions(3)
            .replicas(1)
            .build();
    }
}
