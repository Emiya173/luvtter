package com.luvtter.contract.dto

import kotlinx.serialization.Serializable

/**
 * 客户端首次启动后用来判断是否需要弹「给未来的自己寄第一封信」的引导卡片。
 * showFirstLetterPrompt 由服务端推算: 用户既未主动 dismiss、也尚未寄出过任何一封信。
 */
@Serializable
data class OnboardingStateDto(
    val firstLetterPromptDismissed: Boolean,
    val firstLetterSent: Boolean,
    val showFirstLetterPrompt: Boolean,
)

@Serializable
data class UpdateOnboardingStateRequest(
    val firstLetterPromptDismissed: Boolean? = null,
    val firstLetterSent: Boolean? = null,
)
