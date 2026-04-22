package com.letter.server.mail

import com.letter.server.common.newId
import com.letter.server.common.now
import com.letter.server.db.LetterEvents
import org.jetbrains.exposed.v1.jdbc.insert
import java.time.Duration
import java.time.OffsetDateTime
import kotlin.random.Random
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val POSTCARD_TEMPLATES = listOf(
    "信件正在经过远方的云海" to "风穿过海面,它暂停片刻就继续赶路了。",
    "信件途经一座古老的邮政驿站" to "那里有摇曳的灯,和等待被带走的信。",
    "信件在群山之间拐了个弯" to "远处的雪还没化,它沾了一点冷气。"
)

@OptIn(ExperimentalUuidApi::class)
class EventGenerator {

    /** 寄信时一次性生成事件。返回可能被意外提前修改的最终 deliveryAt。 */
    fun generate(letterId: Uuid, sentAt: OffsetDateTime, initialDeliveryAt: OffsetDateTime, distance: Int): OffsetDateTime {
        val ts = now()
        var deliveryAt = initialDeliveryAt
        val totalHours = Duration.between(sentAt, deliveryAt).toHours()

        // 意外提前: >24h 时 2% 概率
        if (totalHours > 24 && Random.nextDouble() < 0.02) {
            val speedup = 0.1 + Random.nextDouble() * 0.2
            val newHours = (totalHours * (1 - speedup)).toLong().coerceAtLeast(1)
            deliveryAt = sentAt.plusHours(newHours)
            LetterEvents.insert {
                it[id] = newId()
                it[LetterEvents.letterId] = letterId
                it[eventType] = "early_arrival"
                it[title] = "意外提前"
                it[content] = "这封信遇到了顺风,会比预期更早到。"
                it[triggeredAt] = ts
                it[visibleAt] = deliveryAt
                it[createdAt] = ts
            }
        }

        // 途中明信片: D > 500 且 >72h
        if (distance > 500 && totalHours > 72) {
            val count = when {
                totalHours > 240 -> 3
                totalHours > 120 -> 2
                else -> 1
            }
            repeat(count) { i ->
                val ratio = (i + 1).toDouble() / (count + 1)
                val visible = sentAt.plusSeconds((totalHours * 3600 * ratio).toLong())
                val (title, content) = POSTCARD_TEMPLATES.random()
                LetterEvents.insert {
                    it[id] = newId()
                    it[LetterEvents.letterId] = letterId
                    it[eventType] = "postcard"
                    it[LetterEvents.title] = title
                    it[LetterEvents.content] = content
                    it[triggeredAt] = ts
                    it[visibleAt] = visible
                    it[createdAt] = ts
                }
            }
        }

        // 磨损: 48/120/240h 阶梯
        listOf(48L, 120L, 240L).forEachIndexed { i, h ->
            if (totalHours > h) {
                val visible = sentAt.plusHours(h)
                LetterEvents.insert {
                    it[id] = newId()
                    it[LetterEvents.letterId] = letterId
                    it[eventType] = "wear_update"
                    it[title] = "磨损等级 ${i + 1}"
                    it[triggeredAt] = ts
                    it[visibleAt] = visible
                    it[createdAt] = ts
                }
            }
        }

        return deliveryAt
    }
}
