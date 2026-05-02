package com.luvtter.server.user

import com.luvtter.contract.dto.*
import com.luvtter.server.auth.HandleValidator
import com.luvtter.server.auth.userRow
import com.luvtter.server.common.now
import com.luvtter.server.config.ApiException
import com.luvtter.server.config.NotFoundException
import com.luvtter.server.config.ValidationException
import com.luvtter.server.db.UserAddresses
import com.luvtter.server.db.Users
import io.ktor.http.*
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.neq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

data class UserRow(
    val id: Uuid,
    val handle: String,
    val handleFinalized: Boolean,
    val displayName: String,
    val avatarUrl: String?,
    val bio: String?,
    val onlyFriends: Boolean,
    val currentAddressId: Uuid? = null
)

@OptIn(ExperimentalUuidApi::class)
fun UserRow.toDto() = UserDto(
    id = id.toString(),
    handle = handle,
    handleFinalized = handleFinalized,
    displayName = displayName,
    avatarUrl = avatarUrl,
    bio = bio,
    onlyFriends = onlyFriends,
    currentAddressId = currentAddressId?.toString()
)

@OptIn(ExperimentalUuidApi::class)
fun UserRow.publicDto() = UserDto(
    id = id.toString(),
    handle = handle,
    handleFinalized = handleFinalized,
    displayName = displayName,
    avatarUrl = avatarUrl,
    bio = bio,
    onlyFriends = onlyFriends,
    currentAddressId = null
)

@OptIn(ExperimentalUuidApi::class)
class UserService {

    fun get(id: Uuid): UserDto = transaction {
        Users.selectAll().where { Users.id eq id }.firstOrNull()
            ?.let(::userRow)?.toDto()
            ?: throw NotFoundException(message = "用户不存在")
    }

    fun update(id: Uuid, req: UpdateMeRequest): UserDto = transaction {
        Users.update({ Users.id eq id }) {
            req.displayName?.let { v -> it[displayName] = v }
            req.avatarUrl?.let { v -> it[avatarUrl] = v }
            req.bio?.let { v -> it[bio] = v }
            req.onlyFriends?.let { v -> it[onlyFriends] = v }
            it[updatedAt] = now()
        }
        Users.selectAll().where { Users.id eq id }.first().let(::userRow).toDto()
    }

    fun handleAvailable(handle: String): HandleAvailability = transaction {
        if (!HandleValidator.isValid(handle)) {
            return@transaction HandleAvailability(handle, false)
        }
        val taken = Users.selectAll().where { Users.handle eq handle }.firstOrNull() != null
        HandleAvailability(handle, !taken)
    }

    fun finalizeHandle(id: Uuid, req: FinalizeHandleRequest): UserDto = transaction {
        val current = Users.selectAll().where { Users.id eq id }.firstOrNull()
            ?: throw NotFoundException(message = "用户不存在")
        if (current[Users.handleFinalized]) {
            throw ApiException(
                code = "HANDLE_FINALIZED",
                message = "Handle 已确定,不可再修改",
                status = HttpStatusCode.Conflict
            )
        }
        if (!HandleValidator.isValid(req.handle)) {
            throw ValidationException("Handle 格式无效:3-20 字符,允许中英文/数字/下划线")
        }
        val taken = Users.selectAll()
            .where { (Users.handle eq req.handle) and (Users.id neq id) }
            .firstOrNull() != null
        if (taken) {
            throw ApiException(
                code = "HANDLE_TAKEN",
                message = "该 handle 已被占用",
                status = HttpStatusCode.Conflict
            )
        }
        Users.update({ Users.id eq id }) {
            it[handle] = req.handle
            it[handleFinalized] = true
            it[updatedAt] = now()
        }
        Users.selectAll().where { Users.id eq id }.first().let(::userRow).toDto()
    }

    fun setCurrentAddress(userId: Uuid, addressId: Uuid): UserDto = transaction {
        val missing = UserAddresses.selectAll()
            .where { (UserAddresses.id eq addressId) and (UserAddresses.userId eq userId) and (UserAddresses.deletedAt.isNull()) }
            .empty()
        if (missing) throw NotFoundException(message = "地址不存在")
        Users.update({ Users.id eq userId }) {
            it[currentAddressId] = addressId
            it[updatedAt] = now()
        }
        Users.selectAll().where { Users.id eq userId }.first().let(::userRow).toDto()
    }
}
