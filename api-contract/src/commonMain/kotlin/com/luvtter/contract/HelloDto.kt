package com.luvtter.contract.dto

import kotlinx.serialization.Serializable

@Serializable
data class HelloResponse(
    val message: String,
    val serverTime: String,
    val version: String
)
