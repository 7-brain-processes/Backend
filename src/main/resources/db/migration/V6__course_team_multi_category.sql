CREATE TABLE IF NOT EXISTS course_team_categories (
    team_id UUID NOT NULL,
    category_id UUID NOT NULL,
    PRIMARY KEY (team_id, category_id),
    CONSTRAINT fk_course_team_categories_team FOREIGN KEY (team_id) REFERENCES course_teams(id) ON DELETE CASCADE,
    CONSTRAINT fk_course_team_categories_category FOREIGN KEY (category_id) REFERENCES course_categories(id) ON DELETE CASCADE
);

INSERT INTO course_team_categories (team_id, category_id)
SELECT id, category_id FROM course_teams WHERE category_id IS NOT NULL
ON CONFLICT DO NOTHING;
