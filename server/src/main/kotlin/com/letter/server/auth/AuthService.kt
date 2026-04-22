package com.letter.server.auth

import com.letter.contract.dto.*
import com.letter.server.common.newId
import com.letter.server.common.now
import com.letter.server.config.UnauthorizedException
import com.letter.server.config.ValidationException
import com.letter.server.db.AuthCredentials
import com.letter.server.db.AuthSessions
import com.letter.server.db.UserAssets
import com.letter.server.db.UserNotificationPrefs
import com.letter.server.db.Users
import com.letter.server.db.Stamps
import com.letter.server.db.Stationeries
import com.letter.server.db.Stickers
import com.letter.server.user.UserRow
import com.letter.server.user.toDto
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.update
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import java.security.SecureRandom
import java.time.OffsetDateTime
import java.util.Base64
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val EMAIL_REGEX = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
private val HANDLE_REGEX = Regex("^[A-Za-z0-9_\\u4e00-\\u9fa5]{3,20}$")

@OptIn(ExperimentalUuidApi::class)
class AuthService(private val jwt: JwtConfig) {

    fun register(req: RegisterRequest, deviceName: String?, platform: String?): TokenPair = transaction {
        if (!EMAIL_REGEX.matches(req.email)) throw ValidationException("邮箱格式不正确")
        if (req.password.length < 8) throw ValidationException("密码至少 8 位")
        if (req.displayName.isBlank()) throw ValidationException("昵称不能为空")

        val existing = AuthCredentials
            .selectAll()
            .where { (AuthCredentials.provider eq "email") and (AuthCredentials.identifier eq req.email) }
            .firstOrNull()
        if (existing != null) throw ValidationException("该邮箱已注册")

        val userId = newId()
        val tempHandle = "u_" + userId.toString().replace("-", "").take(10)
        val ts = now()
        Users.insert {
            it[id] = userId
            it[handle] = tempHandle
            it[handleFinalized] = false
            it[displayName] = req.displayName
            it[createdAt] = ts
            it[updatedAt] = ts
        }
        AuthCredentials.insert {
            it[id] = newId()
            it[AuthCredentials.userId] = userId
            it[provider] = "email"
            it[identifier] = req.email
            it[secretHash] = Passwords.hash(req.password)
            it[verified] = false
            it[createdAt] = ts
        }
        UserNotificationPrefs.insert {
            it[UserNotificationPrefs.userId] = userId
            it[updatedAt] = ts
        }
        seedDefaultAssets(userId, ts)

        val user = Users.selectAll().where { Users.id eq userId }.first().let(::userRow).toDto()
        issueTokens(userId, user, deviceName, platform)
    }

    fun login(req: LoginRequest): TokenPair = transaction {
        val cred = AuthCredentials
            .selectAll()
            .where { (AuthCredentials.provider eq "email") and (AuthCredentials.identifier eq req.email) }
            .firstOrNull()
            ?: throw UnauthorizedException("邮箱或密码错误")
        val hash = cred[AuthCredentials.secretHash] ?: throw UnauthorizedException("邮箱或密码错误")
        if (!Passwords.verify(hash, req.password)) throw UnauthorizedException("邮箱或密码错误")

        val userId = cred[AuthCredentials.userId]
        val user = Users.selectAll().where { Users.id eq userId }.first().let(::userRow).toDto()
        issueTokens(userId, user, req.deviceName, req.platform)
    }

    fun refresh(req: RefreshRequest): TokenPair = transaction {
        val session = AuthSessions
            .selectAll()
            .where { AuthSessions.refreshToken eq req.refreshToken }
            .firstOrNull()
            ?: throw UnauthorizedException("Refresh token 无效")
        if (session[AuthSessions.expiresAt].isBefore(now())) {
            AuthSessions.deleteWhere { AuthSessions.id eq session[AuthSessions.id] }
            throw UnauthorizedException("Refresh token 已过期")
        }
        val userId = session[AuthSessions.userId]
        val user = Users.selectAll().where { Users.id eq userId }.first().let(::userRow).toDto()
        AuthSessions.update({ AuthSessions.id eq session[AuthSessions.id] }) {
            it[lastActiveAt] = now()
        }
        TokenPair(
            accessToken = jwt.makeAccessToken(userId),
            refreshToken = session[AuthSessions.refreshToken],
            expiresIn = jwt.accessTtlSeconds,
            user = user
        )
    }

    fun logout(refreshToken: String) = transaction {
        AuthSessions.deleteWhere { AuthSessions.refreshToken eq refreshToken }
    }

    fun logoutSession(userId: Uuid, sessionId: Uuid) = transaction {
        AuthSessions.deleteWhere { (AuthSessions.id eq sessionId) and (AuthSessions.userId eq userId) }
    }

    private fun issueTokens(userId: Uuid, user: UserDto, deviceName: String?, platform: String?): TokenPair {
        val refresh = randomToken()
        val ts = now()
        AuthSessions.insert {
            it[id] = newId()
            it[AuthSessions.userId] = userId
            it[refreshToken] = refresh
            it[AuthSessions.deviceName] = deviceName
            it[AuthSessions.platform] = platform
            it[lastActiveAt] = ts
            it[expiresAt] = ts.plusDays(jwt.refreshTtlDays)
            it[createdAt] = ts
        }
        return TokenPair(
            accessToken = jwt.makeAccessToken(userId),
            refreshToken = refresh,
            expiresIn = jwt.accessTtlSeconds,
            user = user
        )
    }

    private fun seedDefaultAssets(userId: Uuid, ts: OffsetDateTime) {
        Stamps.selectAll().where { Stamps.isDefault eq true }.forEach { row ->
            UserAssets.insert {
                it[id] = newId()
                it[UserAssets.userId] = userId
                it[assetType] = "stamp"
                it[assetId] = row[Stamps.id]
                it[quantity] = if (row[Stamps.tier] == 1) 50 else 5
                it[acquiredFrom] = "default"
                it[acquiredAt] = ts
            }
        }
        Stationeries.selectAll().where { Stationeries.isDefault eq true }.forEach { row ->
            UserAssets.insert {
                it[id] = newId()
                it[UserAssets.userId] = userId
                it[assetType] = "stationery"
                it[assetId] = row[Stationeries.id]
                it[quantity] = 1
                it[acquiredFrom] = "default"
                it[acquiredAt] = ts
            }
        }
        Stickers.selectAll().where { Stickers.isDefault eq true }.forEach { row ->
            UserAssets.insert {
                it[id] = newId()
                it[UserAssets.userId] = userId
                it[assetType] = "sticker"
                it[assetId] = row[Stickers.id]
                it[quantity] = 1
                it[acquiredFrom] = "default"
                it[acquiredAt] = ts
            }
        }
    }

    private fun randomToken(): String {
        val bytes = ByteArray(48)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }
}

internal fun userRow(row: org.jetbrains.exposed.v1.core.ResultRow): UserRow = UserRow(
    id = row[Users.id],
    handle = row[Users.handle],
    handleFinalized = row[Users.handleFinalized],
    displayName = row[Users.displayName],
    avatarUrl = row[Users.avatarUrl],
    bio = row[Users.bio],
    onlyFriends = row[Users.onlyFriends]
)

object HandleValidator {
    fun isValid(s: String): Boolean = HANDLE_REGEX.matches(s)
}
