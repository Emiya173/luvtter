package com.luvtter.server.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import io.ktor.server.config.*
import java.util.Date
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class JwtConfig(
    val secret: String,
    val issuer: String,
    val audience: String,
    val realm: String,
    val accessTtlSeconds: Long,
    val refreshTtlDays: Long
) {
    val algorithm: Algorithm = Algorithm.HMAC256(secret)

    @OptIn(ExperimentalUuidApi::class)
    fun makeAccessToken(userId: Uuid): String {
        val now = System.currentTimeMillis()
        return JWT.create()
            .withIssuer(issuer)
            .withAudience(audience)
            .withSubject(userId.toString())
            .withIssuedAt(Date(now))
            .withExpiresAt(Date(now + accessTtlSeconds * 1000))
            .sign(algorithm)
    }
}

fun ApplicationConfig.jwtConfig(): JwtConfig = JwtConfig(
    secret = property("jwt.secret").getString(),
    issuer = property("jwt.issuer").getString(),
    audience = property("jwt.audience").getString(),
    realm = property("jwt.realm").getString(),
    accessTtlSeconds = property("jwt.accessTokenTtlSeconds").getString().toLong(),
    refreshTtlDays = property("jwt.refreshTokenTtlDays").getString().toLong()
)

@OptIn(ExperimentalUuidApi::class)
fun JWTPrincipal.userId(): Uuid = Uuid.parse(payload.subject)

fun Application.configureAuth(jwt: JwtConfig) {
    install(Authentication) {
        jwt("auth-jwt") {
            realm = jwt.realm
            verifier(
                JWT.require(jwt.algorithm)
                    .withIssuer(jwt.issuer)
                    .withAudience(jwt.audience)
                    .build()
            )
            validate { credential ->
                if (credential.payload.subject != null) JWTPrincipal(credential.payload) else null
            }
        }
    }
}
