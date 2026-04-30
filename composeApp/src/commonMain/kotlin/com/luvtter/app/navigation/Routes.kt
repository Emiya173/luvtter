package com.luvtter.app.navigation

import kotlinx.serialization.Serializable

@Serializable
data object LoginRoute

@Serializable
data object RegisterRoute

@Serializable
data object HomeRoute

@Serializable
data class ComposeRoute(
    val replyToLetterId: String? = null,
    val recipientHandle: String? = null,
    val editDraftId: String? = null
)

@Serializable
data class LetterDetailRoute(val id: String)

@Serializable
data object AddressesRoute

@Serializable
data object ContactsRoute

@Serializable
data object SessionsRoute

// 临时 P2 沙盒入口,P3+ 删除
@Serializable
data object LetterPlaygroundRoute
