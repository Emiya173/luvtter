package com.letter.server.user

import com.letter.contract.dto.*
import com.letter.server.common.newId
import com.letter.server.common.now
import com.letter.server.common.parseId
import com.letter.server.config.NotFoundException
import com.letter.server.config.ValidationException
import com.letter.server.db.UserAddresses
import com.letter.server.db.VirtualAnchors
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.isNull
import org.jetbrains.exposed.v1.jdbc.deleteWhere
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class AddressService {

    fun list(userId: Uuid): List<AddressDto> = transaction {
        UserAddresses.selectAll()
            .where { (UserAddresses.userId eq userId) and (UserAddresses.deletedAt.isNull()) }
            .orderBy(UserAddresses.isDefault to SortOrder.DESC, UserAddresses.createdAt to SortOrder.ASC)
            .map(::toDto)
    }

    fun create(userId: Uuid, req: CreateAddressRequest): AddressDto = transaction {
        validate(req)
        val id = newId()
        val ts = now()
        if (req.isDefault) clearDefault(userId)
        UserAddresses.insert {
            it[UserAddresses.id] = id
            it[UserAddresses.userId] = userId
            it[label] = req.label
            it[type] = req.type
            it[latitude] = req.latitude
            it[longitude] = req.longitude
            it[city] = req.city
            it[country] = req.country
            it[anchorId] = req.anchorId?.let(::parseId)
            it[anchorLat] = req.anchorLat
            it[anchorLng] = req.anchorLng
            it[virtualDistance] = req.virtualDistance
            it[isDefault] = req.isDefault
            it[createdAt] = ts
            it[updatedAt] = ts
        }
        // 若是该用户首条地址,自动设为默认
        val count = UserAddresses.selectAll()
            .where { (UserAddresses.userId eq userId) and (UserAddresses.deletedAt.isNull()) }
            .count()
        if (count == 1L && !req.isDefault) {
            UserAddresses.update({ UserAddresses.id eq id }) { it[isDefault] = true }
        }
        load(id)
    }

    fun update(userId: Uuid, addressId: Uuid, req: UpdateAddressRequest): AddressDto = transaction {
        val row = UserAddresses.selectAll()
            .where { (UserAddresses.id eq addressId) and (UserAddresses.userId eq userId) and (UserAddresses.deletedAt.isNull()) }
            .firstOrNull() ?: throw NotFoundException(message = "地址不存在")
        UserAddresses.update({ UserAddresses.id eq addressId }) {
            req.label?.let { v -> it[label] = v }
            req.latitude?.let { v -> it[latitude] = v }
            req.longitude?.let { v -> it[longitude] = v }
            req.city?.let { v -> it[city] = v }
            req.country?.let { v -> it[country] = v }
            req.anchorId?.let { v -> it[anchorId] = parseId(v) }
            req.anchorLat?.let { v -> it[anchorLat] = v }
            req.anchorLng?.let { v -> it[anchorLng] = v }
            req.virtualDistance?.let { v -> it[virtualDistance] = v }
            it[updatedAt] = now()
        }
        load(addressId)
    }

    fun softDelete(userId: Uuid, addressId: Uuid) = transaction {
        val updated = UserAddresses.update({
            (UserAddresses.id eq addressId) and (UserAddresses.userId eq userId) and (UserAddresses.deletedAt.isNull())
        }) {
            it[deletedAt] = now()
            it[isDefault] = false
        }
        if (updated == 0) throw NotFoundException(message = "地址不存在")
    }

    fun setDefault(userId: Uuid, addressId: Uuid): AddressDto = transaction {
        UserAddresses.selectAll()
            .where { (UserAddresses.id eq addressId) and (UserAddresses.userId eq userId) and (UserAddresses.deletedAt.isNull()) }
            .firstOrNull() ?: throw NotFoundException(message = "地址不存在")
        clearDefault(userId)
        UserAddresses.update({ UserAddresses.id eq addressId }) {
            it[isDefault] = true
            it[updatedAt] = now()
        }
        load(addressId)
    }

    fun listAnchors(): List<VirtualAnchorDto> = transaction {
        VirtualAnchors.selectAll().orderBy(VirtualAnchors.orderIndex to SortOrder.ASC).map {
            VirtualAnchorDto(
                id = it[VirtualAnchors.id].toString(),
                code = it[VirtualAnchors.code],
                name = it[VirtualAnchors.name],
                description = it[VirtualAnchors.description],
                latitude = it[VirtualAnchors.latitude],
                longitude = it[VirtualAnchors.longitude],
                imageUrl = it[VirtualAnchors.imageUrl]
            )
        }
    }

    private fun clearDefault(userId: Uuid) {
        UserAddresses.update({
            (UserAddresses.userId eq userId) and (UserAddresses.isDefault eq true) and (UserAddresses.deletedAt.isNull())
        }) {
            it[isDefault] = false
        }
    }

    private fun load(id: Uuid): AddressDto = UserAddresses.selectAll()
        .where { UserAddresses.id eq id }
        .first()
        .let(::toDto)

    private fun toDto(row: org.jetbrains.exposed.v1.core.ResultRow) = AddressDto(
        id = row[UserAddresses.id].toString(),
        label = row[UserAddresses.label],
        type = row[UserAddresses.type],
        latitude = row[UserAddresses.latitude],
        longitude = row[UserAddresses.longitude],
        city = row[UserAddresses.city],
        country = row[UserAddresses.country],
        anchorId = row[UserAddresses.anchorId]?.toString(),
        anchorLat = row[UserAddresses.anchorLat],
        anchorLng = row[UserAddresses.anchorLng],
        virtualDistance = row[UserAddresses.virtualDistance],
        isDefault = row[UserAddresses.isDefault]
    )

    private fun validate(req: CreateAddressRequest) {
        when (req.type) {
            "real" -> {
                if (req.latitude == null || req.longitude == null) {
                    throw ValidationException("真实地址需要经纬度")
                }
            }
            "virtual" -> {
                val hasAnchor = req.anchorId != null || (req.anchorLat != null && req.anchorLng != null)
                if (!hasAnchor) throw ValidationException("虚拟地址需要锚点(anchorId 或 anchorLat/anchorLng)")
                val d = req.virtualDistance ?: throw ValidationException("虚拟地址需要 virtualDistance")
                if (d !in 0..1000) throw ValidationException("virtualDistance 范围 0-1000")
            }
            else -> throw ValidationException("type 仅支持 real | virtual")
        }
        if (req.label.isBlank() || req.label.length > 32) throw ValidationException("label 1-32 字符")
    }
}
