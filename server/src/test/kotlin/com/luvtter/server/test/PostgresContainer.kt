package com.luvtter.server.test

import org.flywaydb.core.Flyway
import org.testcontainers.containers.PostgreSQLContainer

object PostgresContainer {
    val container: PostgreSQLContainer<*> by lazy {
        PostgreSQLContainer("postgres:16-alpine").apply {
            withDatabaseName("letter_test")
            withUsername("test")
            withPassword("test")
            withReuse(false)
            start()
            Runtime.getRuntime().addShutdownHook(Thread { runCatching { stop() } })
            Flyway.configure()
                .dataSource(jdbcUrl, username, password)
                .locations("classpath:db/migration")
                .load()
                .migrate()
        }
    }
}
