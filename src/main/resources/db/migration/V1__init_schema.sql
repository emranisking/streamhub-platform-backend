-- =========================================================
-- StreamHub Platform - Initial Schema
-- =========================================================

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- ---------------------------------------------------------
-- users
-- Note: users does NOT follow the common BaseEntity shape.
-- id = internal numeric PK (fast joins), uid = public-facing UUID.
-- ---------------------------------------------------------
CREATE TABLE users (
    id                    BIGSERIAL PRIMARY KEY,
    uid                   UUID NOT NULL DEFAULT gen_random_uuid(),
    username              VARCHAR(64) NOT NULL,
    email                 VARCHAR(255) NOT NULL,
    password              VARCHAR(255) NOT NULL,
    role_type             VARCHAR(32) NOT NULL DEFAULT 'NORMAL_USER',
    is_active             BOOLEAN NOT NULL DEFAULT TRUE,
    is_subscribed         BOOLEAN NOT NULL DEFAULT FALSE,
    subscription_tier     VARCHAR(32),
    subscription_expiry   TIMESTAMP,
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT uq_users_uid UNIQUE (uid),
    CONSTRAINT uq_users_username UNIQUE (username),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_role_type CHECK (role_type IN ('ADMIN', 'ANALYTIC', 'NORMAL_USER'))
);

CREATE INDEX idx_users_email ON users (email);
CREATE INDEX idx_users_created_at ON users (created_at);

-- ---------------------------------------------------------
-- categories
-- ---------------------------------------------------------
CREATE TABLE categories (
    id          UUID PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    deleted     BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT uq_categories_name UNIQUE (name)
);

-- ---------------------------------------------------------
-- videos
-- ---------------------------------------------------------
CREATE TABLE videos (
    id                UUID PRIMARY KEY,
    title             VARCHAR(255) NOT NULL,
    description       TEXT,
    video_url         VARCHAR(1024),
    thumbnail_url     VARCHAR(1024),
    duration_seconds  INTEGER,
    category_id       UUID,
    views             BIGINT NOT NULL DEFAULT 0,
    likes             BIGINT NOT NULL DEFAULT 0,
    source_filename   VARCHAR(512),
    created_at        TIMESTAMP NOT NULL DEFAULT now(),
    updated_at        TIMESTAMP NOT NULL DEFAULT now(),
    deleted           BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_videos_category FOREIGN KEY (category_id) REFERENCES categories (id) ON DELETE SET NULL,
    CONSTRAINT uq_videos_source_filename UNIQUE (source_filename)
);

CREATE INDEX idx_videos_category_id ON videos (category_id);
CREATE INDEX idx_videos_created_at ON videos (created_at);

-- ---------------------------------------------------------
-- subscriptions
-- ---------------------------------------------------------
CREATE TABLE subscriptions (
    id          UUID PRIMARY KEY,
    plan        VARCHAR(32) NOT NULL DEFAULT 'basic',
    is_active   BOOLEAN NOT NULL DEFAULT TRUE,
    start_date  TIMESTAMP,
    end_date    TIMESTAMP,
    user_id     BIGINT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    deleted     BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_subscriptions_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_subscriptions_user_id ON subscriptions (user_id);
CREATE INDEX idx_subscriptions_active ON subscriptions (user_id, is_active);

-- ---------------------------------------------------------
-- playlists / playlist_items
-- ---------------------------------------------------------
CREATE TABLE playlists (
    id          UUID PRIMARY KEY,
    title       VARCHAR(255) NOT NULL,
    user_id     BIGINT NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    deleted     BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_playlists_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
);

CREATE INDEX idx_playlists_user_id ON playlists (user_id);

CREATE TABLE playlist_items (
    id           UUID PRIMARY KEY,
    playlist_id  UUID NOT NULL,
    video_id     UUID NOT NULL,
    position     INTEGER NOT NULL,
    created_at   TIMESTAMP NOT NULL DEFAULT now(),
    updated_at   TIMESTAMP NOT NULL DEFAULT now(),
    deleted      BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_playlist_items_playlist FOREIGN KEY (playlist_id) REFERENCES playlists (id) ON DELETE CASCADE,
    CONSTRAINT fk_playlist_items_video FOREIGN KEY (video_id) REFERENCES videos (id) ON DELETE CASCADE
);

CREATE INDEX idx_playlist_items_playlist_id ON playlist_items (playlist_id);
CREATE UNIQUE INDEX uq_playlist_items_playlist_video ON playlist_items (playlist_id, video_id) WHERE deleted = FALSE;

-- ---------------------------------------------------------
-- watch_history
-- ---------------------------------------------------------
CREATE TABLE watch_history (
    id          UUID PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    video_id    UUID NOT NULL,
    watched_at  TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    deleted     BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_watch_history_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_watch_history_video FOREIGN KEY (video_id) REFERENCES videos (id) ON DELETE CASCADE,
    CONSTRAINT uq_watch_history_user_video UNIQUE (user_id, video_id)
);

CREATE INDEX idx_watch_history_user_id ON watch_history (user_id, watched_at DESC);

-- ---------------------------------------------------------
-- user_likes
-- ---------------------------------------------------------
CREATE TABLE user_likes (
    id          UUID PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    video_id    UUID NOT NULL,
    liked_at    TIMESTAMP NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now(),
    deleted     BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_user_likes_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    CONSTRAINT fk_user_likes_video FOREIGN KEY (video_id) REFERENCES videos (id) ON DELETE CASCADE,
    CONSTRAINT uq_user_likes_user_video UNIQUE (user_id, video_id)
);

CREATE INDEX idx_user_likes_user_id ON user_likes (user_id, liked_at DESC);

-- ---------------------------------------------------------
-- visit_logs (analytics)
-- ---------------------------------------------------------
CREATE TABLE visit_logs (
    id                 UUID PRIMARY KEY,
    user_id            BIGINT,
    session_id         VARCHAR(128),
    ip_address         VARCHAR(64),
    visited_at         TIMESTAMP NOT NULL,
    new_registration   BOOLEAN NOT NULL DEFAULT FALSE,
    created_at         TIMESTAMP NOT NULL DEFAULT now(),
    updated_at         TIMESTAMP NOT NULL DEFAULT now(),
    deleted            BOOLEAN NOT NULL DEFAULT FALSE,
    CONSTRAINT fk_visit_logs_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL
);

CREATE INDEX idx_visit_logs_visited_at ON visit_logs (visited_at);
CREATE INDEX idx_visit_logs_user_id ON visit_logs (user_id);
CREATE INDEX idx_visit_logs_new_registration ON visit_logs (new_registration, visited_at);
