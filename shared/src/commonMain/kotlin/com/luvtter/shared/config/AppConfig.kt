package com.luvtter.shared.config

import kotlinx.serialization.Serializable

/**
 * 应用本地配置。Desktop 由 `~/.config/luvtter/luvtter.toml`(或 cwd 下 `luvtter.toml`)读取,
 * 不存在时一律走默认值。Android/iOS 目前固定走默认值,后续可按平台习惯接 SharedPreferences/UserDefaults。
 *
 * 字段全部带默认值,允许配置文件只写感兴趣的部分。
 */
@Serializable
data class AppConfig(
    val server: ServerConfig = ServerConfig(),
    val devAuth: DevAuthConfig = DevAuthConfig(),
    val features: FeatureFlags = FeatureFlags(),
) {
    /** 后端服务器配置。 */
    @Serializable
    data class ServerConfig(
        /** 覆盖各平台的 `apiBaseUrl` 默认值;为空字符串时沿用 expect/actual 平台默认。 */
        val baseUrl: String = "",
    )

    /** 登录页测试账号自动填充。**仅供本地开发**,不要在仓库提交真实凭据。 */
    @Serializable
    data class DevAuthConfig(
        val enabled: Boolean = false,
        val email: String = "",
        val password: String = "",
    )

    /** 功能开关。 */
    @Serializable
    data class FeatureFlags(
        /** 寄件箱在途信件是否显示「加 速 到 达」按钮。生产建议关闭。 */
        val showExpedite: Boolean = true,
        /** 登录页是否展示「申领入籍」入口。设为 false 后客户端隐藏注册路径(适合服务器禁用注册的部署)。 */
        val allowRegistration: Boolean = true,
    )

    companion object {
        val Default: AppConfig = AppConfig()
    }
}
