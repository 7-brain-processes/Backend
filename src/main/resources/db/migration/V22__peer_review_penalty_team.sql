ALTER TABLE peer_review_penalties
    ADD COLUMN IF NOT EXISTS team_id UUID REFERENCES course_teams(id) ON DELETE CASCADE;

CREATE INDEX IF NOT EXISTS idx_prp_post_team ON peer_review_penalties(post_id, team_id);

ALTER TABLE peer_review_penalties
    DROP CONSTRAINT IF EXISTS chk_penalty_user_or_team;

ALTER TABLE peer_review_penalties
    ADD CONSTRAINT chk_penalty_user_or_team CHECK (
        (user_id IS NOT NULL AND team_id IS NULL) OR
        (user_id IS NULL AND team_id IS NOT NULL)
    );
