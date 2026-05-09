package com.cozy.planner.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy(
            @Value("${app.flyway.clean-on-start:true}") boolean cleanOnStart) {
        return flyway -> {
            if (cleanOnStart) {
                flyway.clean();
            }
            flyway.migrate();
        };
    }
}
