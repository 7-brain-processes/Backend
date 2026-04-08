CREATE TABLE team_student_grades (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    team_grade_id UUID NOT NULL REFERENCES team_grades(id) ON DELETE CASCADE,
    student_id    UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    grade         INT CHECK (grade IS NULL OR (grade >= 0 AND grade <= 100)),
    UNIQUE (team_grade_id, student_id)
);

CREATE INDEX idx_team_student_grades_team_grade ON team_student_grades(team_grade_id);
CREATE INDEX idx_team_student_grades_student ON team_student_grades(student_id);
