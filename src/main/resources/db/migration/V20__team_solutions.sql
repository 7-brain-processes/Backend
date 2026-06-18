ALTER TABLE solutions
    ADD COLUMN IF NOT EXISTS team_id UUID REFERENCES course_teams(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_solutions_team ON solutions(team_id);

-- Future submissions for a team task may link either a student or a team.
-- The existing unique constraint (post_id, student_id) remains for individual submissions.
