ALTER TABLE posts
    ADD COLUMN team_formation_mode VARCHAR(20);

UPDATE posts
SET team_formation_mode = 'FREE'
WHERE type = 'TASK' AND team_formation_mode IS NULL;

ALTER TABLE posts
    ADD CONSTRAINT chk_posts_team_formation_mode
        CHECK (team_formation_mode IS NULL OR team_formation_mode IN ('FREE', 'DRAFT', 'RANDOM_SHUFFLE'));

CREATE TABLE team_grades (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id           UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    team_id           UUID NOT NULL REFERENCES course_teams(id) ON DELETE CASCADE,
    grade             INT CHECK (grade >= 0 AND grade <= 100),
    comment           VARCHAR(5000),
    distribution_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (post_id, team_id)
);

CREATE INDEX idx_team_grades_post ON team_grades(post_id);
CREATE INDEX idx_team_grades_team ON team_grades(team_id);

CREATE TABLE team_requirement_templates (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id            UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    name                 VARCHAR(200) NOT NULL,
    description          VARCHAR(2000),
    min_team_size        INT,
    max_team_size        INT,
    required_category_id UUID REFERENCES course_categories(id) ON DELETE SET NULL,
    require_audio        BOOLEAN NOT NULL DEFAULT FALSE,
    require_video        BOOLEAN NOT NULL DEFAULT FALSE,
    active               BOOLEAN NOT NULL DEFAULT TRUE,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    archived_at          TIMESTAMPTZ,
    UNIQUE (course_id, name),
    CHECK (min_team_size IS NULL OR min_team_size > 0),
    CHECK (max_team_size IS NULL OR max_team_size > 0),
    CHECK (min_team_size IS NULL OR max_team_size IS NULL OR min_team_size <= max_team_size)
);

CREATE INDEX idx_team_requirement_templates_course ON team_requirement_templates(course_id);
