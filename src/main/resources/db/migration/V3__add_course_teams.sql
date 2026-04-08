CREATE TABLE course_teams (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id   UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    name        VARCHAR(200) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (course_id, name)
);

CREATE INDEX idx_course_teams_course ON course_teams(course_id);

ALTER TABLE course_members
    ADD COLUMN team_id UUID REFERENCES course_teams(id) ON DELETE SET NULL;

CREATE INDEX idx_course_members_team ON course_members(team_id);
