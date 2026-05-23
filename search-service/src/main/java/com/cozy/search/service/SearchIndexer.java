package com.cozy.search.service;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch.core.DeleteRequest;
import co.elastic.clients.elasticsearch.core.IndexRequest;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import com.cozy.search.config.ElasticsearchConfig;
import jakarta.annotation.PostConstruct;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.elasticsearch.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;

@Service
public class SearchIndexer {

    private static final Logger log = LoggerFactory.getLogger(SearchIndexer.class);

    private final ElasticsearchConfig config;
    private final ElasticsearchClient client;

    public SearchIndexer(ElasticsearchConfig config) {
        this.config = config;
        RestClient restClient = RestClient.builder(
                org.apache.http.HttpHost.create(config.getHost())
        ).build();
        this.client = new ElasticsearchClient(new RestClientTransport(restClient, new JacksonJsonpMapper()));
    }

    @PostConstruct
    public void initIndex() {
        try {
            boolean exists = client.indices().exists(ExistsRequest.of(e -> e.index(config.getSessionsIndex()))).value();
            if (!exists) {
                client.indices().create(CreateIndexRequest.of(c -> c
                        .index(config.getSessionsIndex())
                        .mappings(m -> m
                                .properties("id", p -> p.long_(v -> v))
                                .properties("title", p -> p.text(v -> v))
                                .properties("description", p -> p.text(v -> v))
                                .properties("workoutDate", p -> p.keyword(v -> v))
                                .properties("startTime", p -> p.keyword(v -> v))
                                .properties("endTime", p -> p.keyword(v -> v))
                                .properties("mentorId", p -> p.long_(v -> v))
                                .properties("locationId", p -> p.long_(v -> v))
                                .properties("confirmationStatus", p -> p.keyword(v -> v))
                                .properties("createdBy", p -> p.keyword(v -> v))
                        )
                ));
                log.info("Created index: {}", config.getSessionsIndex());
            }
        } catch (Exception e) {
            log.warn("Could not create index (may already exist or ES not ready): {}", e.getMessage());
        }
    }

    public void indexSession(Map<String, Object> session) {
        try {
            Object id = session.get("id");
            if (id == null) {
                log.warn("Session has no id, skipping indexing");
                return;
            }
            client.index(IndexRequest.of(i -> i
                    .index(config.getSessionsIndex())
                    .id(String.valueOf(id))
                    .document(session)
            ));
            log.info("Indexed session {} in {}", id, config.getSessionsIndex());
        } catch (Exception e) {
            log.error("Failed to index session: {}", e.getMessage());
        }
    }

    public void deleteSession(String id) {
        try {
            client.delete(DeleteRequest.of(d -> d
                    .index(config.getSessionsIndex())
                    .id(id)
            ));
            log.info("Deleted session {} from {}", id, config.getSessionsIndex());
        } catch (ElasticsearchException e) {
            if (e.status() == 404) {
                log.debug("Session {} not found in index, skipping delete", id);
            } else {
                log.error("Failed to delete session {}: {}", id, e.getMessage());
            }
        } catch (Exception e) {
            log.error("Failed to delete session {}: {}", id, e.getMessage());
        }
    }
}
