ALTER TABLE course_teams
    ADD COLUMN post_id UUID REFERENCES posts(id) ON DELETE CASCADE,
    ADD COLUMN max_size INT CHECK (max_size IS NULL OR max_size > 0),
    ADD COLUMN self_enrollment_enabled BOOLEAN NOT NULL DEFAULT false;

CREATE INDEX idx_course_teams_post ON course_teams(post_id);

CREATE INDEX idx_course_teams_post_enrollment ON course_teams(post_id, self_enrollment_enabled)
WHERE post_id IS NOT NULL AND self_enrollment_enabled = true;

ALTER TABLE course_teams
    ADD CONSTRAINT unique_team_per_post_name UNIQUE (course_id, post_id, name)
    DEFERRABLE INITIALLY DEFERRED;

