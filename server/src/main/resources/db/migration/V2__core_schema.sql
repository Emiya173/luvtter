-- V2: 联系人、邮票/信纸/贴纸、信件、整理、通知、异步任务

CREATE TABLE contacts (
    id          UUID PRIMARY KEY,
    owner_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    note        VARCHAR(64),
    relation    VARCHAR(16),
    tags        TEXT[],
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(owner_id, target_id)
);
CREATE INDEX idx_contacts_owner ON contacts(owner_id);

CREATE TABLE blocks (
    id          UUID PRIMARY KEY,
    owner_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    target_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(owner_id, target_id)
);
CREATE INDEX idx_blocks_owner ON blocks(owner_id);

CREATE TABLE stamps (
    id              UUID PRIMARY KEY,
    code            VARCHAR(32) UNIQUE NOT NULL,
    name            VARCHAR(64) NOT NULL,
    tier            INTEGER NOT NULL,
    image_url       TEXT NOT NULL,
    weight_capacity INTEGER NOT NULL,
    speed_factor    DOUBLE PRECISION NOT NULL,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    is_limited      BOOLEAN NOT NULL DEFAULT FALSE,
    available_from  TIMESTAMPTZ,
    available_to    TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stationeries (
    id              UUID PRIMARY KEY,
    code            VARCHAR(32) UNIQUE NOT NULL,
    name            VARCHAR(64) NOT NULL,
    background_url  TEXT NOT NULL,
    thumbnail_url   TEXT NOT NULL,
    is_default      BOOLEAN NOT NULL DEFAULT FALSE,
    is_limited      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE stickers (
    id          UUID PRIMARY KEY,
    code        VARCHAR(32) UNIQUE NOT NULL,
    name        VARCHAR(64) NOT NULL,
    image_url   TEXT NOT NULL,
    weight      INTEGER NOT NULL,
    is_default  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_assets (
    id              UUID PRIMARY KEY,
    user_id         UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    asset_type      VARCHAR(16) NOT NULL,
    asset_id        UUID NOT NULL,
    quantity        INTEGER NOT NULL DEFAULT 1,
    acquired_from   VARCHAR(32),
    acquired_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, asset_type, asset_id)
);
CREATE INDEX idx_user_assets_user ON user_assets(user_id, asset_type);

CREATE TABLE daily_rewards (
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    reward_date DATE NOT NULL,
    claimed_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, reward_date)
);

CREATE TABLE letters (
    id                   UUID PRIMARY KEY,
    sender_id            UUID REFERENCES users(id),
    recipient_id         UUID REFERENCES users(id),
    recipient_address_id UUID REFERENCES user_addresses(id),
    sender_address_id    UUID REFERENCES user_addresses(id),
    stamp_id             UUID REFERENCES stamps(id),
    stationery_id        UUID REFERENCES stationeries(id),
    status               VARCHAR(16) NOT NULL DEFAULT 'draft',
    sealed_until         TIMESTAMPTZ,
    sent_at              TIMESTAMPTZ,
    delivery_at          TIMESTAMPTZ,
    delivered_at         TIMESTAMPTZ,
    read_at              TIMESTAMPTZ,
    distance_value       INTEGER,
    total_weight         INTEGER NOT NULL DEFAULT 0,
    reply_to_letter_id   UUID REFERENCES letters(id),
    wear_level           INTEGER NOT NULL DEFAULT 0,
    sender_hidden_at     TIMESTAMPTZ,
    recipient_hidden_at  TIMESTAMPTZ,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_letters_recipient
    ON letters(recipient_id, delivery_at DESC)
    WHERE status IN ('delivered', 'read') AND recipient_hidden_at IS NULL;
CREATE INDEX idx_letters_sender
    ON letters(sender_id, created_at DESC)
    WHERE sender_hidden_at IS NULL;
CREATE INDEX idx_letters_pending_delivery
    ON letters(delivery_at)
    WHERE status = 'in_transit';
CREATE INDEX idx_letters_reply_to
    ON letters(reply_to_letter_id)
    WHERE reply_to_letter_id IS NOT NULL;

CREATE TABLE letter_contents (
    letter_id    UUID PRIMARY KEY REFERENCES letters(id) ON DELETE CASCADE,
    content_type VARCHAR(16) NOT NULL,
    font_code    VARCHAR(32),
    body_json    JSONB,
    body_url     TEXT,
    index_text   TEXT,
    index_tsv    TSVECTOR,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_letter_contents_tsv ON letter_contents USING GIN(index_tsv);

CREATE TABLE letter_attachments (
    id              UUID PRIMARY KEY,
    letter_id       UUID NOT NULL REFERENCES letters(id) ON DELETE CASCADE,
    attachment_type VARCHAR(16) NOT NULL,
    media_url       TEXT,
    thumbnail_url   TEXT,
    sticker_id      UUID REFERENCES stickers(id),
    position_x      DOUBLE PRECISION,
    position_y      DOUBLE PRECISION,
    rotation        DOUBLE PRECISION,
    weight          INTEGER NOT NULL,
    order_index     INTEGER NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_attachments_letter ON letter_attachments(letter_id);

CREATE TABLE letter_events (
    id           UUID PRIMARY KEY,
    letter_id    UUID NOT NULL REFERENCES letters(id) ON DELETE CASCADE,
    event_type   VARCHAR(32) NOT NULL,
    title        VARCHAR(128),
    content      TEXT,
    image_url    TEXT,
    triggered_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    visible_at   TIMESTAMPTZ NOT NULL,
    read_at      TIMESTAMPTZ,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_events_letter ON letter_events(letter_id);
CREATE INDEX idx_events_visible
    ON letter_events(letter_id, visible_at)
    WHERE read_at IS NULL;

CREATE TABLE folders (
    id          UUID PRIMARY KEY,
    user_id     UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name        VARCHAR(32) NOT NULL,
    icon        VARCHAR(32),
    order_index INTEGER NOT NULL DEFAULT 0,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(user_id, name)
);
CREATE INDEX idx_folders_user ON folders(user_id);

CREATE TABLE letter_folders (
    letter_id UUID NOT NULL REFERENCES letters(id) ON DELETE CASCADE,
    user_id   UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    folder_id UUID NOT NULL REFERENCES folders(id) ON DELETE CASCADE,
    PRIMARY KEY (letter_id, user_id)
);

CREATE TABLE favorites (
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    letter_id  UUID NOT NULL REFERENCES letters(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, letter_id)
);

CREATE TABLE user_notification_prefs (
    user_id     UUID PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    new_letter  BOOLEAN NOT NULL DEFAULT TRUE,
    postcard    BOOLEAN NOT NULL DEFAULT TRUE,
    reply       BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id         UUID PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type       VARCHAR(32) NOT NULL,
    letter_id  UUID REFERENCES letters(id) ON DELETE CASCADE,
    event_id   UUID REFERENCES letter_events(id) ON DELETE CASCADE,
    title      VARCHAR(128) NOT NULL,
    preview    VARCHAR(256),
    read_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user_unread
    ON notifications(user_id, created_at DESC)
    WHERE read_at IS NULL;

CREATE TABLE async_tasks (
    id            UUID PRIMARY KEY,
    task_type     VARCHAR(32) NOT NULL,
    payload       JSONB NOT NULL,
    status        VARCHAR(16) NOT NULL DEFAULT 'pending',
    attempts      INTEGER NOT NULL DEFAULT 0,
    max_attempts  INTEGER NOT NULL DEFAULT 3,
    last_error    TEXT,
    scheduled_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    started_at    TIMESTAMPTZ,
    finished_at   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_tasks_pending
    ON async_tasks(scheduled_at, id)
    WHERE status = 'pending';
