package com.luvtter.server.tasks

import com.luvtter.server.common.now
import com.luvtter.server.db.LetterContents
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.update
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

private val log = KotlinLogging.logger {}

/**
 * `ocr_index` 任务处理器骨架。
 *
 * 当前为占位实现:把扫描信的 `index_text` 写成固定占位串,只是把 OCR 字段串入全文索引,
 * 让扫描信也能通过 §9 的搜索被命中。真实 OCR (Python image-worker) 接入后,只需替换
 * 此函数体,从 MinIO 拉 `letter_contents.scan_object_key` 指向的对象、识别后回写文本即可。
 */
@OptIn(ExperimentalUuidApi::class)
class OcrIndexService {

    fun process(payload: JsonElement) {
        val letterIdStr = payload.jsonObject["letter_id"]?.jsonPrimitive?.content
            ?: error("ocr_index payload 缺少 letter_id")
        val letterId = Uuid.parse(letterIdStr)
        val updated = LetterContents.update({ LetterContents.letterId eq letterId }) {
            it[indexText] = PLACEHOLDER_OCR_TEXT
            it[updatedAt] = now()
        }
        if (updated == 0) {
            log.warn { "ocr_index.process letter=$letterId 未找到 letter_contents 行" }
        } else {
            log.info { "ocr_index.process letter=$letterId 写入占位 OCR 文本" }
        }
    }

    fun lastIndexTextOf(letterId: Uuid): String? = LetterContents.selectAll()
        .where { LetterContents.letterId eq letterId }
        .firstOrNull()
        ?.get(LetterContents.indexText)

    companion object {
        const val PLACEHOLDER_OCR_TEXT =
            "[扫描信占位 OCR 文本 待 image-worker 落地]"
    }
}
