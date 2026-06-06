package com.cozy.planner.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    @Value("${app.notification-topic:notification-events}")
    private String notificationTopic;

    @Value("${app.search-topic:search-events}")
    private String searchTopic;

    @Bean
    public NewTopic notificationEventsTopic() {
        return TopicBuilder.name(notificationTopic).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic searchEventsTopic() {
        return TopicBuilder.name(searchTopic).partitions(1).replicas(1).build();
    }
}
