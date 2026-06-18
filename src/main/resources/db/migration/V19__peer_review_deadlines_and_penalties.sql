ALTER TABLE peer_review_configs
    ADD COLUMN IF NOT EXISTS missed_review_penalty NUMERIC(10, 2) DEFAULT 10 NOT NULL,
    ADD COLUMN IF NOT EXISTS round1_closed_at TIMESTAMPTZ,
    ADD COLUMN IF NOT EXISTS round2_closed_at TIMESTAMPTZ;

CREATE TABLE IF NOT EXISTS peer_review_penalties (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id       UUID         NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    penalty_points NUMERIC(10, 2) NOT NULL,
    reason        VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_prp_post_user ON peer_review_penalties(post_id, user_id);
CREATE INDEX IF NOT EXISTS idx_prp_post ON peer_review_penalties(post_id);
