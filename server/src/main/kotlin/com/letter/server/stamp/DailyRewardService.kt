package com.letter.server.stamp

import com.letter.contract.dto.DailyRewardDto
import com.letter.contract.dto.UserAssetDto
import com.letter.server.common.newId
import com.letter.server.common.now
import com.letter.server.db.DailyRewards
import com.letter.server.db.Stamps
import com.letter.server.db.UserAssets
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.LocalDate
import java.time.ZoneId
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

@OptIn(ExperimentalUuidApi::class)
class DailyRewardService {

    fun claim(userId: Uuid, timezone: String?): DailyRewardDto = transaction {
        val zone = runCatching { ZoneId.of(timezone ?: "UTC") }.getOrDefault(ZoneId.of("UTC"))
        val today = LocalDate.now(zone).toString()
        val already = DailyRewards.selectAll()
            .where { (DailyRewards.userId eq userId) and (DailyRewards.rewardDate eq today) }
            .firstOrNull()
        if (already != null) {
            return@transaction DailyRewardDto(claimed = false)
        }
        DailyRewards.insert {
            it[DailyRewards.userId] = userId
            it[DailyRewards.rewardDate] = today
            it[claimedAt] = now()
        }

        // 固定奖励：平信 x5
        val plain = Stamps.selectAll().where { Stamps.code eq "plain" }.firstOrNull()
            ?: return@transaction DailyRewardDto(claimed = true)
        val plainId = plain[Stamps.id]
        val existing = UserAssets.selectAll()
            .where {
                (UserAssets.userId eq userId) and (UserAssets.assetType eq "stamp") and (UserAssets.assetId eq plainId)
            }
            .firstOrNull()
        if (existing != null) {
            UserAssets.update({ UserAssets.id eq existing[UserAssets.id] }) {
                it[quantity] = existing[UserAssets.quantity] + 5
            }
        } else {
            UserAssets.insert {
                it[id] = newId()
                it[UserAssets.userId] = userId
                it[assetType] = "stamp"
                it[assetId] = plainId
                it[quantity] = 5
                it[acquiredFrom] = "daily"
                it[acquiredAt] = now()
            }
        }
        log.info { "daily.claim user=$userId date=$today grant=plain_stamp+5" }
        DailyRewardDto(
            claimed = true,
            grants = listOf(UserAssetDto(assetType = "stamp", assetId = plainId.toString(), quantity = 5))
        )
    }
}
