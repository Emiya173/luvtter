-- V1: 基础用户、认证、地址

CREATE TABLE users (
    id                UUID PRIMARY KEY,
    handle            VARCHAR(64) UNIQUE NOT NULL,
    handle_finalized  BOOLEAN NOT NULL DEFAULT FALSE,
    display_name      VARCHAR(64) NOT NULL,
    avatar_url        TEXT,
    bio               TEXT,
    only_friends      BOOLEAN NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_handle ON users(handle);

CREATE TABLE auth_credentials (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    provider     VARCHAR(16) NOT NULL,
    identifier   VARCHAR(255) NOT NULL,
    secret_hash  TEXT,
    verified     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE(provider, identifier)
);
CREATE INDEX idx_auth_user ON auth_credentials(user_id);

CREATE TABLE auth_sessions (
    id             UUID PRIMARY KEY,
    user_id        UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token  VARCHAR(128) UNIQUE NOT NULL,
    device_name    VARCHAR(64),
    platform       VARCHAR(16),
    last_active_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at     TIMESTAMPTZ NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_sessions_user ON auth_sessions(user_id);
CREATE INDEX idx_sessions_expires ON auth_sessions(expires_at);

CREATE TABLE virtual_anchors (
    id           UUID PRIMARY KEY,
    code         VARCHAR(32) UNIQUE NOT NULL,
    name         VARCHAR(64) NOT NULL,
    description  TEXT,
    latitude     DOUBLE PRECISION NOT NULL,
    longitude    DOUBLE PRECISION NOT NULL,
    image_url    TEXT,
    order_index  INTEGER NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE user_addresses (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    label            VARCHAR(32) NOT NULL,
    type             VARCHAR(16) NOT NULL,
    latitude         DOUBLE PRECISION,
    longitude        DOUBLE PRECISION,
    city             VARCHAR(64),
    country          VARCHAR(64),
    anchor_id        UUID REFERENCES virtual_anchors(id),
    anchor_lat       DOUBLE PRECISION,
    anchor_lng       DOUBLE PRECISION,
    virtual_distance INTEGER,
    is_default       BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at       TIMESTAMPTZ,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_addresses_user ON user_addresses(user_id) WHERE deleted_at IS NULL;
CREATE UNIQUE INDEX idx_addresses_default
    ON user_addresses(user_id)
    WHERE is_default = TRUE AND deleted_at IS NULL;
