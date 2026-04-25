-- 扫描信底层存储引用:
-- letter_contents.scan_object_key 指向 MinIO 上的扫描原图/PDF;
-- detail 接口读取时按此 key 重签发 GET URL,前端负责拉取。
-- 为 Stage 4 OCR 管线预留: image-worker 可按 letterId/objectKey 取原文件做透视矫正与 OCR。

ALTER TABLE letter_contents
    ADD COLUMN IF NOT EXISTS scan_object_key TEXT;
