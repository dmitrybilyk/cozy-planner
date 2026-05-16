package com.cozy.planner.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.r2dbc.convert.R2dbcCustomConversions;
import org.springframework.data.r2dbc.dialect.PostgresDialect;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

@Configuration
public class R2dbcConfig {

    @Bean
    public R2dbcCustomConversions r2dbcCustomConversions() {
        return R2dbcCustomConversions.of(PostgresDialect.INSTANCE, List.of(
            new Converter<OffsetDateTime, LocalDateTime>() {
                @Override
                public LocalDateTime convert(OffsetDateTime source) {
                    return source.toLocalDateTime();
                }
            }
        ));
    }
}