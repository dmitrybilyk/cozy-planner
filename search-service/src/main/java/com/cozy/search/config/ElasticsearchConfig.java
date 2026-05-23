package com.cozy.search.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "elasticsearch")
public class ElasticsearchConfig {
    private String host;
    private String sessionsIndex;

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getSessionsIndex() { return sessionsIndex; }
    public void setSessionsIndex(String sessionsIndex) { this.sessionsIndex = sessionsIndex; }
}
