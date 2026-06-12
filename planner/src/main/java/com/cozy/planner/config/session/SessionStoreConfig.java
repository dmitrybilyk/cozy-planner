package com.cozy.planner.config.session;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;
import org.springframework.web.server.session.DefaultWebSessionManager;
import org.springframework.web.server.session.WebSessionIdResolver;
import org.springframework.web.server.session.WebSessionManager;

@Configuration
public class SessionStoreConfig {

    @Bean(name = WebHttpHandlerBuilder.WEB_SESSION_MANAGER_BEAN_NAME)
    public WebSessionManager webSessionManager(R2dbcEntityTemplate template,
                                               WebSessionIdResolver webSessionIdResolver) {
        DefaultWebSessionManager manager = new DefaultWebSessionManager();
        manager.setSessionStore(new PersistentWebSessionStore(template));
        manager.setSessionIdResolver(webSessionIdResolver);
        return manager;
    }
}
