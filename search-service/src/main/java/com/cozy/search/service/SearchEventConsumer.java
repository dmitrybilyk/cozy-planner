package com.cozy.search.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class SearchEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(SearchEventConsumer.class);

    private final SearchIndexer searchIndexer;

    public SearchEventConsumer(SearchIndexer searchIndexer) {
        this.searchIndexer = searchIndexer;
    }

    @SuppressWarnings("unchecked")
    @KafkaListener(topics = "${app.search-topic:search-events}", groupId = "${spring.kafka.consumer.group-id:search-group}")
    public void consume(Map<String, Object> event) {
        String action = (String) event.get("action");
        Map<String, Object> session = (Map<String, Object>) event.get("session");

        if (session == null) {
            log.warn("Received search event without session data, action={}", action);
            return;
        }

        log.info("Consumed search event: action={}, sessionId={}", action, session.get("id"));

        switch (action != null ? action : "") {
            case "CREATED":
            case "UPDATED":
                searchIndexer.indexSession(session);
                break;
            case "DELETED":
                Object id = session.get("id");
                if (id != null) {
                    searchIndexer.deleteSession(String.valueOf(id));
                }
                break;
            default:
                log.warn("Unknown search event action: {}", action);
        }
    }
}
