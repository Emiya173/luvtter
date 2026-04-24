package com.letter.server.test

import com.letter.server.module
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.sse.SSE
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import java.sql.DriverManager

private val testJson = Json {
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}

private fun testConfig(): MapApplicationConfig {
    val c = PostgresContainer.container
    return MapApplicationConfig(
        "database.url" to c.jdbcUrl,
        "database.user" to c.username,
        "database.password" to c.password,
        "database.maxPoolSize" to "4",
        "storage.endpoint" to "http://localhost:9000",
        "storage.accessKey" to "test",
        "storage.secretKey" to "test",
        "storage.bucket" to "test",
        "jwt.secret" to "test-secret-please-change",
        "jwt.issuer" to "letter-test",
        "jwt.audience" to "letter-test-users",
        "jwt.realm" to "letter-test",
        "jwt.accessTokenTtlSeconds" to "3600",
        "jwt.refreshTokenTtlDays" to "1"
    )
}

private val USER_TABLES = listOf(
    "async_tasks",
    "notifications",
    "user_notification_prefs",
    "favorites",
    "letter_folders",
    "folders",
    "letter_events",
    "letter_attachments",
    "letter_contents",
    "letters",
    "daily_rewards",
    "user_assets",
    "blocks",
    "contacts",
    "user_addresses",
    "auth_sessions",
    "auth_credentials",
    "users"
)

fun truncateUserData() {
    val c = PostgresContainer.container
    DriverManager.getConnection(c.jdbcUrl, c.username, c.password).use { conn ->
        conn.createStatement().use { stmt ->
            stmt.execute("TRUNCATE TABLE ${USER_TABLES.joinToString()} RESTART IDENTITY CASCADE")
        }
    }
}

fun runServerTest(block: suspend ApplicationTestBuilder.(client: HttpClient) -> Unit) {
    truncateUserData()
    testApplication {
        environment { config = testConfig() }
        application { module() }
        val client = createClient {
            install(ContentNegotiation) { json(testJson) }
            install(SSE)
        }
        block(client)
    }
}
