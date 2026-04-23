ALTER TABLE users
    ADD COLUMN current_address_id UUID REFERENCES user_addresses(id);

ALTER TABLE notifications
    ADD COLUMN address_id UUID REFERENCES user_addresses(id);

CREATE INDEX IF NOT EXISTS idx_notifications_user_address
    ON notifications(user_id, address_id, created_at DESC);

-- 已有用户回填：默认地址 → 当前位置
UPDATE users u
SET current_address_id = a.id
FROM user_addresses a
WHERE a.user_id = u.id
  AND a.is_default = TRUE
  AND a.deleted_at IS NULL
  AND u.current_address_id IS NULL;
