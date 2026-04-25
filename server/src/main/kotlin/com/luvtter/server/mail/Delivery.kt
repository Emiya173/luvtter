package com.luvtter.server.mail

import java.time.Duration
import java.time.OffsetDateTime
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

private fun baseHours(d: Int): Double = when {
    d < 50 -> 3.0 + d * 0.1
    d < 200 -> 8.0 + (d - 50) * 0.2
    d < 500 -> 38.0 + (d - 200) * 0.4
    d < 800 -> 158.0 + (d - 500) * 0.3
    else -> 248.0 + (d - 800) * 0.25
}

private fun stampFactor(tier: Int): Double = when (tier) {
    1 -> 1.00
    2 -> 0.60
    3 -> 0.35
    4 -> 0.15
    else -> 1.00
}

fun computeDeliveryAt(sentAt: OffsetDateTime, distance: Int, tier: Int): OffsetDateTime {
    val rand = 0.9 + Random.nextDouble() * 0.2
    val hours = (baseHours(distance) * stampFactor(tier) * rand)
        .let { max(1.0, min(14 * 24.0, it)) }
    val seconds = (hours * 3600).toLong()
    return sentAt.plusSeconds(seconds)
}

fun transitStage(now: OffsetDateTime, sentAt: OffsetDateTime, deliveryAt: OffsetDateTime): String {
    val totalSec = Duration.between(sentAt, deliveryAt).seconds.coerceAtLeast(1)
    val elapsed = Duration.between(sentAt, now).seconds.coerceAtLeast(0)
    val ratio = elapsed.toDouble() / totalSec
    return when {
        ratio < 0.15 -> "sending"
        ratio < 0.8 -> "on_the_way"
        else -> "arriving"
    }
}

fun wearLevelForHours(hours: Long): Int = when {
    hours > 240 -> 3
    hours > 120 -> 2
    hours > 48 -> 1
    else -> 0
}
