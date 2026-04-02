CREATE TABLE post_captains (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID        NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    captain_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    assigned_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (post_id, captain_id)
);

CREATE INDEX idx_post_captains_post ON post_captains(post_id);
CREATE INDEX idx_post_captains_captain ON post_captains(captain_id);