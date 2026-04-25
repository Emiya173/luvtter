-- 手写信底层存储引用:
-- letter_contents.handwriting_object_key 指向 MinIO 上的笔画数据 JSON (或可选的 PNG 渲染);
-- detail 接口读取时按此 key 重签发 GET URL,前端按 contentType=handwriting 解析笔画。
-- Stage 3 笔迹输入接入时,客户端 sign-put → 上传 JSON → addHandwriting(objectKey)。

ALTER TABLE letter_contents
    ADD COLUMN IF NOT EXISTS handwriting_object_key TEXT;
