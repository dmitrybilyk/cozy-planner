package com.cozy.notifications.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@Configuration
@ConditionalOnProperty(name = "app.notification-service", havingValue = "kafka", matchIfMissing = true)
@EnableKafka
public class KafkaConfig {
}
