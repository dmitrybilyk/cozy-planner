package com.linkease.server.kafka

import com.linkease.server.config.AppProperties
import org.apache.kafka.clients.admin.NewTopic
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.TopicBuilder

@EnableKafka
@Configuration
class KafkaConfig(private val appProperties: AppProperties) {

    @Bean
    fun notificationTopic(): NewTopic =
        TopicBuilder.name(appProperties.notificationTopic).partitions(1).replicas(1).build()
}
