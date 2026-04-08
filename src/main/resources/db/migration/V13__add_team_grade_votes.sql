CREATE TABLE team_grade_votes (
    id            UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    team_grade_id UUID        NOT NULL REFERENCES team_grades(id) ON DELETE CASCADE,
    voter_id      UUID        NOT NULL REFERENCES users(id)       ON DELETE CASCADE,
    submitted_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (team_grade_id, voter_id)
);

CREATE INDEX idx_team_grade_votes_team_grade ON team_grade_votes(team_grade_id);
CREATE INDEX idx_team_grade_votes_voter       ON team_grade_votes(voter_id);

CREATE TABLE team_grade_vote_entries (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    vote_id    UUID NOT NULL REFERENCES team_grade_votes(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES users(id)            ON DELETE CASCADE,
    grade      INT  NOT NULL CHECK (grade >= 0 AND grade <= 100)
);

CREATE INDEX idx_team_grade_vote_entries_vote    ON team_grade_vote_entries(vote_id);
CREATE INDEX idx_team_grade_vote_entries_student ON team_grade_vote_entries(student_id);
