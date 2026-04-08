ALTER TABLE posts DROP CONSTRAINT IF EXISTS chk_posts_team_formation_mode;

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_team_formation_mode
        CHECK (team_formation_mode IS NULL OR team_formation_mode IN ('FREE', 'DRAFT', 'RANDOM_SHUFFLE', 'CAPTAIN_SELECTION'));