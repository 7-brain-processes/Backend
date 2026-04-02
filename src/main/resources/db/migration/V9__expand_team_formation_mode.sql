ALTER TABLE posts
    ALTER COLUMN team_formation_mode TYPE VARCHAR(30);

ALTER TABLE posts
    DROP CONSTRAINT chk_posts_team_formation_mode;

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_team_formation_mode
        CHECK (team_formation_mode IS NULL OR team_formation_mode IN ('FREE', 'DRAFT', 'RANDOM_SHUFFLE', 'RANDOM_CAPTAIN_SELECTION'));