package com.linkease.server.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.sql.DriverManager

@Configuration
class DatabaseConfig {

    @Bean
    fun flywayMigrationStrategy(
        @Value("\${spring.datasource.url}") jdbcUrl: String,
        @Value("\${spring.datasource.username}") username: String,
        @Value("\${spring.datasource.password}") password: String,
    ) = FlywayMigrationStrategy { flyway ->
        ensureDatabaseExists(jdbcUrl, username, password)
        flyway.migrate()
    }

    private fun ensureDatabaseExists(jdbcUrl: String, username: String, password: String) {
        val dbName = jdbcUrl.substringAfterLast("/").substringBefore("?")
        // Connect to the postgres maintenance database to check/create the target db.
        val maintenanceUrl = jdbcUrl.replaceAfterLast("/", "postgres")
        DriverManager.getConnection(maintenanceUrl, username, password).use { conn ->
            conn.autoCommit = true
            conn.createStatement().use { stmt ->
                val exists = stmt.executeQuery(
                    "SELECT 1 FROM pg_database WHERE datname = '$dbName'"
                ).next()
                if (!exists) {
                    stmt.execute("""CREATE DATABASE "$dbName"""")
                    println("Created database: $dbName")
                }
            }
        }
    }
}
