package com.luvtter.server

import com.luvtter.contract.dto.ApiResponse
import com.luvtter.contract.dto.DailyRewardDto
import com.luvtter.contract.dto.MyAssetsDto
import com.luvtter.contract.dto.RegisterRequest
import com.luvtter.contract.dto.StampDto
import com.luvtter.contract.dto.TokenPair
import com.luvtter.server.test.runServerTest
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.bearerAuth
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * 验证 `POST /api/v1/me/daily-reward` 在不同时区下的幂等性。
 * 服务端用 `LocalDate.now(ZoneId.of(tz))` 计算 reward_date,以 (userId, rewardDate) 做主键。
 */
class DailyRewardTimezoneTest {

    @Test
    fun `same timezone twice in a row is idempotent`() = runServerTest { client ->
        val u = register(client, "dr-same@example.com", "User")
        val plainStampId = client.get("/api/v1/stamps")
            .body<ApiResponse<List<StampDto>>>().data.first { it.code == "plain" }.id

        // 注册赠送 50 张平信
        assertEquals(50, plainStampQuantity(client, u.accessToken, plainStampId))

        val first = claim(client, u.accessToken, header = "UTC")
        assertTrue(first.claimed, "首次同 tz claim 必须成功")
        assertEquals(55, plainStampQuantity(client, u.accessToken, plainStampId))

        val second = claim(client, u.accessToken, header = "UTC")
        assertFalse(second.claimed, "同 tz 同日第二次必须 claimed=false")
        assertTrue(second.grants.isEmpty(), "幂等失败时不应再次发奖")
        assertEquals(55, plainStampQuantity(client, u.accessToken, plainStampId))
    }

    @Test
    fun `cross day timezones grant twice for same user`() = runServerTest { client ->
        val u = register(client, "dr-cross@example.com", "User")
        val plainStampId = client.get("/api/v1/stamps")
            .body<ApiResponse<List<StampDto>>>().data.first { it.code == "plain" }.id

        // Etc/GMT-14 = UTC+14, Etc/GMT+12 = UTC-12,任意 UTC 时刻两者本地日期相差 1 天 → 必跨日。
        val east = claim(client, u.accessToken, header = "Etc/GMT-14")
        val west = claim(client, u.accessToken, header = "Etc/GMT+12")
        assertTrue(east.claimed, "东端时区首次必须成功")
        assertTrue(west.claimed, "西端时区落到不同 reward_date 时也必须成功")
        assertEquals(60, plainStampQuantity(client, u.accessToken, plainStampId))

        // 再补一刀验证幂等仍然生效
        val again = claim(client, u.accessToken, header = "Etc/GMT+12")
        assertFalse(again.claimed)
        assertEquals(60, plainStampQuantity(client, u.accessToken, plainStampId))
    }

    @Test
    fun `invalid timezone falls back to UTC and stays idempotent`() = runServerTest { client ->
        val u = register(client, "dr-bad-tz@example.com", "User")
        val plainStampId = client.get("/api/v1/stamps")
            .body<ApiResponse<List<StampDto>>>().data.first { it.code == "plain" }.id

        val utc = claim(client, u.accessToken, header = "UTC")
        assertTrue(utc.claimed)

        // 非法 tz 应该被 service 兜底为 UTC,因此与上面同日 → claimed=false
        val mars = claim(client, u.accessToken, header = "Mars/Olympus")
        assertFalse(mars.claimed, "非法时区必须降级为 UTC,而不是去当成另一个有效时区给二次奖励")
        assertEquals(55, plainStampQuantity(client, u.accessToken, plainStampId))
    }

    @Test
    fun `header and query param produce the same effect, default UTC`() = runServerTest { client ->
        val u = register(client, "dr-default@example.com", "User")
        val plainStampId = client.get("/api/v1/stamps")
            .body<ApiResponse<List<StampDto>>>().data.first { it.code == "plain" }.id

        // 不传 tz → service 默认 UTC
        val none = claim(client, u.accessToken, header = null, query = null)
        assertTrue(none.claimed)

        // header=UTC 和不传等价 → 第二次必须幂等
        val viaHeader = claim(client, u.accessToken, header = "UTC")
        assertFalse(viaHeader.claimed)

        // query=UTC 同样幂等
        val viaQuery = claim(client, u.accessToken, header = null, query = "UTC")
        assertFalse(viaQuery.claimed)

        assertEquals(55, plainStampQuantity(client, u.accessToken, plainStampId))
    }

    // --- helpers ---

    private suspend fun claim(
        client: HttpClient,
        token: String,
        header: String? = null,
        query: String? = null,
    ): DailyRewardDto {
        val path = if (query != null) "/api/v1/me/daily-reward?tz=$query" else "/api/v1/me/daily-reward"
        val resp = client.post(path) {
            bearerAuth(token)
            contentType(ContentType.Application.Json)
            if (header != null) header("X-User-Timezone", header)
        }
        assertEquals(HttpStatusCode.OK, resp.status, "claim 应返回 200,实际 ${resp.status}")
        val dto = resp.body<ApiResponse<DailyRewardDto>>().data
        assertNotNull(dto)
        return dto
    }

    private suspend fun plainStampQuantity(
        client: HttpClient,
        token: String,
        plainStampId: String,
    ): Int {
        val assets = client.get("/api/v1/me/assets") { bearerAuth(token) }
            .body<ApiResponse<MyAssetsDto>>().data
        return assets.stamps.firstOrNull { it.assetId == plainStampId }?.quantity ?: 0
    }

    private data class RegisteredUser(val accessToken: String)

    private suspend fun register(client: HttpClient, email: String, displayName: String): RegisteredUser {
        val resp = client.post("/api/v1/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(RegisterRequest(email = email, password = "password123", displayName = displayName))
        }
        assertEquals(HttpStatusCode.Created, resp.status)
        val tokens = resp.body<ApiResponse<TokenPair>>().data
        return RegisteredUser(tokens.accessToken)
    }
}
