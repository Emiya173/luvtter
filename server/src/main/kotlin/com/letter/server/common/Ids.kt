package com.letter.server.common

import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
fun newId(): Uuid = Uuid.random()

@OptIn(ExperimentalUuidApi::class)
fun parseId(s: String): Uuid = Uuid.parse(s)

@OptIn(ExperimentalUuidApi::class)
fun parseIdOrNull(s: String?): Uuid? = s?.let { runCatching { Uuid.parse(it) }.getOrNull() }
