package com.luvtter.server.tasks

import com.luvtter.contract.dto.OcrTaskStatusDto
import com.luvtter.server.common.now
import com.luvtter.server.db.AsyncTasks
import com.luvtter.server.db.Letters
import com.luvtter.server.config.NotFoundException
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import java.time.OffsetDateTime
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/**
 * 简单的进程内异步任务执行器,Postgres 行锁原子认领 + 失败重试。
 * Stage 4 的 image-worker 接入后,Python 端可直接走同一行锁协议(SKIP LOCKED)拉取任务。
 */
@OptIn(ExperimentalUuidApi::class)
class AsyncTaskRunner(
    private val ocrIndexService: OcrIndexService,
    private val pollMillis: Long = 2_000L,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null

    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            log.info { "tasks.runner started pollMillis=$pollMillis" }
            while (isActive) {
                val processed = runCatching { tickOnce() }.getOrElse { e ->
                    log.error(e) { "tasks.runner tick failed" }
                    false
                }
                if (!processed) delay(pollMillis)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    /** 拿一条 pending 任务并执行。返回 true 表示有处理过任务,可以立刻继续;false 则空轮询。 */
    private fun tickOnce(): Boolean {
        val claimed = transaction { claimNext() } ?: return false
        val (taskId, taskType, payload, attempts, maxAttempts) = claimed
        val result = runCatching {
            transaction {
                when (taskType) {
                    "ocr_index" -> ocrIndexService.process(payload)
                    else -> log.warn { "tasks.runner unknown taskType=$taskType id=$taskId" }
                }
            }
        }
        transaction {
            if (result.isSuccess) {
                AsyncTasks.update({ AsyncTasks.id eq taskId }) {
                    it[status] = "done"
                    it[finishedAt] = now()
                    it[updatedAt] = now()
                    it[lastError] = null
                }
            } else {
                val err = result.exceptionOrNull()?.message?.take(500) ?: "unknown"
                val nextStatus = if (attempts >= maxAttempts) "failed" else "pending"
                AsyncTasks.update({ AsyncTasks.id eq taskId }) {
                    it[status] = nextStatus
                    it[lastError] = err
                    it[updatedAt] = now()
                    if (nextStatus == "failed") it[finishedAt] = now()
                    if (nextStatus == "pending") {
                        // 退避: attempts 秒后重试
                        it[scheduledAt] = OffsetDateTime.now().plusSeconds(attempts.toLong() * 2 + 1)
                    }
                }
                log.warn { "tasks.runner task=$taskId attempts=$attempts/$maxAttempts -> $nextStatus err=$err" }
            }
        }
        return true
    }

    private data class ClaimedTask(
        val id: Uuid,
        val taskType: String,
        val payload: JsonElement,
        val attempts: Int,
        val maxAttempts: Int,
    )

    /**
     * 用 PG 的 `FOR UPDATE SKIP LOCKED` 单语句原子认领,允许多实例并发(未来可能上 K8s 多副本)。
     */
    private fun claimNext(): ClaimedTask? {
        var result: ClaimedTask? = null
        // 显式指定 StatementType.SELECT,让 Exposed 走 executeQuery() 以读取 RETURNING 结果集
        org.jetbrains.exposed.v1.jdbc.transactions.TransactionManager.current().exec(
            """
            UPDATE async_tasks SET
                status = 'in_progress',
                started_at = now(),
                attempts = attempts + 1,
                updated_at = now()
            WHERE id = (
                SELECT id FROM async_tasks
                WHERE status = 'pending' AND scheduled_at <= now()
                ORDER BY scheduled_at, id
                FOR UPDATE SKIP LOCKED
                LIMIT 1
            )
            RETURNING id, task_type, payload::text AS payload, attempts, max_attempts
            """.trimIndent(),
            explicitStatementType = org.jetbrains.exposed.v1.core.statements.StatementType.SELECT
        ) { rs ->
            if (rs.next()) {
                val payloadStr = rs.getString("payload")
                result = ClaimedTask(
                    id = Uuid.parse(rs.getObject("id").toString()),
                    taskType = rs.getString("task_type"),
                    payload = kotlinx.serialization.json.Json.parseToJsonElement(payloadStr),
                    attempts = rs.getInt("attempts"),
                    maxAttempts = rs.getInt("max_attempts"),
                )
            }
            null as Unit?
        }
        return result
    }
}

@OptIn(ExperimentalUuidApi::class)
class OcrTaskQuery {
    /**
     * 查询某封信最近一条 ocr_index 任务的状态。
     * 必须先校验 viewer 是 sender 或 recipient。
     */
    fun statusFor(viewerId: Uuid, letterId: Uuid): OcrTaskStatusDto = transaction {
        val letter = Letters.selectAll().where { Letters.id eq letterId }.firstOrNull()
            ?: throw NotFoundException(message = "信件不存在")
        val authorized = letter[Letters.senderId] == viewerId ||
            letter[Letters.recipientId] == viewerId
        if (!authorized) throw NotFoundException(message = "信件不存在")

        val row = AsyncTasks.selectAll()
            .where { AsyncTasks.taskType eq "ocr_index" }
            .orderBy(AsyncTasks.createdAt to SortOrder.DESC)
            .firstOrNull { it[AsyncTasks.payload].letterIdMatches(letterId) }
            ?: throw NotFoundException(message = "未找到 OCR 任务")

        OcrTaskStatusDto(
            taskId = row[AsyncTasks.id].toString(),
            status = row[AsyncTasks.status],
            attempts = row[AsyncTasks.attempts],
            maxAttempts = row[AsyncTasks.maxAttempts],
            lastError = row[AsyncTasks.lastError],
            finishedAt = row[AsyncTasks.finishedAt]?.toString(),
        )
    }

    private fun JsonElement.letterIdMatches(id: Uuid): Boolean = runCatching {
        jsonObject["letter_id"]?.jsonPrimitive?.content == id.toString()
    }.getOrDefault(false)
}
