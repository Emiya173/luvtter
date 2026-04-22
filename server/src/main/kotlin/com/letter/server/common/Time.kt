package com.letter.server.common

import java.time.OffsetDateTime
import java.time.ZoneOffset

fun now(): OffsetDateTime = OffsetDateTime.now(ZoneOffset.UTC)

fun OffsetDateTime?.iso(): String? = this?.toString()
