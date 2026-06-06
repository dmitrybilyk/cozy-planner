package com.cozy.planner.service;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class KafkaAvailabilityService {

    private static final Logger log = LoggerFactory.getLogger(KafkaAvailabilityService.class);
    private static final int TIMEOUT_SECONDS = 3;

    private final KafkaAdmin kafkaAdmin;
    private final AtomicBoolean available = new AtomicBoolean(false);

    public KafkaAvailabilityService(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    public boolean isAvailable() {
        return available.get();
    }

    public void markUnavailable() {
        if (available.compareAndSet(true, false)) {
            log.warn("Kafka marked unavailable — routing to direct Telegram");
        }
    }

    @Scheduled(fixedDelay = 15000, initialDelay = 0)
    public void checkHealth() {
        Map<String, Object> config = new HashMap<>(kafkaAdmin.getConfigurationProperties());
        config.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, String.valueOf(TIMEOUT_SECONDS * 1000));
        config.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, String.valueOf(TIMEOUT_SECONDS * 1000));
        config.put("reconnect.backoff.max.ms", "500");
        config.put("socket.connection.setup.timeout.ms", "2000");
        config.put("socket.connection.setup.timeout.max.ms", "2000");
        // No retries in health check — fail fast, let next scheduled cycle retry
        config.put(AdminClientConfig.RETRIES_CONFIG, "0");

        try (AdminClient client = AdminClient.create(config)) {
            client.describeCluster().clusterId().get(TIMEOUT_SECONDS + 1, TimeUnit.SECONDS);
            if (available.compareAndSet(false, true)) {
                log.info("Kafka is available — routing notifications via Kafka");
            }
        } catch (Exception e) {
            if (available.compareAndSet(true, false)) {
                log.warn("Kafka health check failed — routing to direct Telegram: {}", e.getMessage());
            }
        }
    }
}
