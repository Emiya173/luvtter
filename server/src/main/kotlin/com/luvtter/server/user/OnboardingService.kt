package com.luvtter.server.user

import com.luvtter.contract.dto.OnboardingStateDto
import com.luvtter.contract.dto.UpdateOnboardingStateRequest
import com.luvtter.server.common.now
import com.luvtter.server.db.UserOnboardingStates
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
class OnboardingService {

    fun get(userId: Uuid): OnboardingStateDto = transaction {
        ensureRow(userId)
        loadDto(userId)
    }

    fun update(userId: Uuid, req: UpdateOnboardingStateRequest): OnboardingStateDto = transaction {
        ensureRow(userId)
        UserOnboardingStates.update({ UserOnboardingStates.userId eq userId }) {
            req.firstLetterPromptDismissed?.let { v -> it[firstLetterPromptDismissed] = v }
            req.firstLetterSent?.let { v -> it[firstLetterSent] = v }
            it[updatedAt] = now()
        }
        loadDto(userId)
    }

    /** 寄出任意一封信后调用,幂等。 */
    fun markFirstLetterSent(userId: Uuid) = transaction {
        ensureRow(userId)
        UserOnboardingStates.update({ UserOnboardingStates.userId eq userId }) {
            it[firstLetterSent] = true
            it[updatedAt] = now()
        }
    }

    private fun ensureRow(userId: Uuid) {
        val exists = UserOnboardingStates.selectAll()
            .where { UserOnboardingStates.userId eq userId }.firstOrNull() != null
        if (!exists) {
            UserOnboardingStates.insert {
                it[UserOnboardingStates.userId] = userId
                it[updatedAt] = now()
            }
        }
    }

    private fun loadDto(userId: Uuid): OnboardingStateDto {
        val row = UserOnboardingStates.selectAll()
            .where { UserOnboardingStates.userId eq userId }.first()
        val dismissed = row[UserOnboardingStates.firstLetterPromptDismissed]
        val sent = row[UserOnboardingStates.firstLetterSent]
        return OnboardingStateDto(
            firstLetterPromptDismissed = dismissed,
            firstLetterSent = sent,
            showFirstLetterPrompt = !dismissed && !sent,
        )
    }
}
