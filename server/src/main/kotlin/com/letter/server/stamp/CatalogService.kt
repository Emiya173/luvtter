package com.letter.server.stamp

import com.letter.contract.dto.*
import com.letter.server.db.Stamps
import com.letter.server.db.Stationeries
import com.letter.server.db.Stickers
import com.letter.server.db.UserAssets
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class CatalogService {

    fun listStamps(): List<StampDto> = transaction {
        Stamps.selectAll().orderBy(Stamps.tier to SortOrder.ASC).map {
            StampDto(
                id = it[Stamps.id].toString(),
                code = it[Stamps.code],
                name = it[Stamps.name],
                tier = it[Stamps.tier],
                imageUrl = it[Stamps.imageUrl],
                weightCapacity = it[Stamps.weightCapacity],
                speedFactor = it[Stamps.speedFactor],
                isLimited = it[Stamps.isLimited]
            )
        }
    }

    fun listStationeries(): List<StationeryDto> = transaction {
        Stationeries.selectAll().map {
            StationeryDto(
                id = it[Stationeries.id].toString(),
                code = it[Stationeries.code],
                name = it[Stationeries.name],
                backgroundUrl = it[Stationeries.backgroundUrl],
                thumbnailUrl = it[Stationeries.thumbnailUrl],
                isLimited = it[Stationeries.isLimited]
            )
        }
    }

    fun listStickers(): List<StickerDto> = transaction {
        Stickers.selectAll().map {
            StickerDto(
                id = it[Stickers.id].toString(),
                code = it[Stickers.code],
                name = it[Stickers.name],
                imageUrl = it[Stickers.imageUrl],
                weight = it[Stickers.weight]
            )
        }
    }

    fun myAssets(userId: Uuid): MyAssetsDto = transaction {
        val rows = UserAssets.selectAll().where { UserAssets.userId eq userId }.toList()
        fun by(t: String) = rows.filter { it[UserAssets.assetType] == t }.map {
            UserAssetDto(
                assetType = t,
                assetId = it[UserAssets.assetId].toString(),
                quantity = it[UserAssets.quantity]
            )
        }
        MyAssetsDto(stamps = by("stamp"), stationeries = by("stationery"), stickers = by("sticker"))
    }
}
