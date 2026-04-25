-- 通知免打扰时段:
-- quiet_start / quiet_end 为本地 [0,23] 小时,半开区间 [start, end);
-- 若 start > end 表示跨日 (例如 22 -> 7 表示 22:00-06:59 静默);
-- timezone 为 IANA 时区 (例如 Asia/Shanghai),为空时按 UTC 处理。
-- 静默窗口内,新通知仍然落库,但不再通过 SSE 实时推送。

ALTER TABLE user_notification_prefs
    ADD COLUMN IF NOT EXISTS quiet_start SMALLINT,
    ADD COLUMN IF NOT EXISTS quiet_end   SMALLINT,
    ADD COLUMN IF NOT EXISTS timezone    VARCHAR(64);
