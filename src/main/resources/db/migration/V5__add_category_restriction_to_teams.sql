ALTER TABLE course_teams
    ADD COLUMN category_id UUID REFERENCES course_categories(id) ON DELETE SET NULL;

CREATE INDEX idx_course_teams_category ON course_teams(category_id);
