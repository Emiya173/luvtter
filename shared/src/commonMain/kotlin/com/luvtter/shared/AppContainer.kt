package com.luvtter.shared

import com.luvtter.shared.auth.TokenStore
import com.luvtter.shared.network.*

class AppContainer(baseUrl: String) {
    val tokens = TokenStore()
    private val http = createHttpClient(baseUrl) { tokens.accessToken() }

    val auth = AuthApi(http)
    val me = MeApi(http)
    val addresses = AddressApi(http)
    val contacts = ContactApi(http)
    val catalog = CatalogApi(http)
    val letters = LetterApi(http)
    val folders = FolderApi(http)
    val notifications = NotificationApi(http)
    val dailyReward = DailyRewardApi(http)
}
