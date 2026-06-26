package com.linkease.server.kafka

import org.apache.kafka.clients.admin.AdminClient
import org.apache.kafka.clients.admin.AdminClientConfig
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaAdmin
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

@Service
class KafkaAvailabilityService(private val kafkaAdmin: KafkaAdmin) {

    private val log = LoggerFactory.getLogger(KafkaAvailabilityService::class.java)
    private val available = AtomicBoolean(false)

    fun isAvailable() = available.get()

    fun markUnavailable() {
        if (available.compareAndSet(true, false)) {
            log.warn("Kafka marked unavailable — routing to direct Telegram")
        }
    }

    @Scheduled(fixedDelay = 15_000, initialDelay = 0)
    fun checkHealth() {
        val config = HashMap(kafkaAdmin.configurationProperties).apply {
            put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, "3000")
            put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, "3000")
            put("reconnect.backoff.max.ms", "500")
            put("socket.connection.setup.timeout.ms", "2000")
            put("socket.connection.setup.timeout.max.ms", "2000")
            put(AdminClientConfig.RETRIES_CONFIG, "0")
        }
        val bootstrap = config[AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG]
        log.debug("Kafka health check → {} ({})", bootstrap, if (available.get()) "UP" else "DOWN")
        try {
            AdminClient.create(config).use { client ->
                client.describeCluster().clusterId().get(4, TimeUnit.SECONDS)
                if (available.compareAndSet(false, true))
                    log.info("Kafka AVAILABLE — routing notifications via Kafka")
            }
        } catch (e: Exception) {
            if (available.compareAndSet(true, false))
                log.warn("Kafka UNAVAILABLE — routing to direct Telegram: {}", e.message)
        }
    }
}
