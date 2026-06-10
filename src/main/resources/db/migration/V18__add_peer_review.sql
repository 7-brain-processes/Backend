DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'criteria'::regclass AND contype = 'c'
    LOOP
        EXECUTE 'ALTER TABLE criteria DROP CONSTRAINT ' || quote_ident(r.conname);
    END LOOP;
END $$;

ALTER TABLE criteria ADD CONSTRAINT criteria_type_check
    CHECK (type IN ('YES_NO', 'PERCENTAGE', 'POINTS', 'PEER_REVIEW'));

DO $$
DECLARE
    r RECORD;
BEGIN
    FOR r IN SELECT conname FROM pg_constraint
             WHERE conrelid = 'versioned_criteria'::regclass AND contype = 'c'
    LOOP
        EXECUTE 'ALTER TABLE versioned_criteria DROP CONSTRAINT ' || quote_ident(r.conname);
    END LOOP;
END $$;

ALTER TABLE versioned_criteria ADD CONSTRAINT versioned_criteria_type_check
    CHECK (type IN ('YES_NO', 'PERCENTAGE', 'POINTS', 'PEER_REVIEW'));

CREATE TABLE peer_review_configs (
    id                   UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    criterion_id         UUID         NOT NULL UNIQUE REFERENCES criteria(id) ON DELETE CASCADE,
    reviewers_count      INT          NOT NULL DEFAULT 1,
    scoring_strategy     VARCHAR(20)  NOT NULL DEFAULT 'AVERAGE'
                             CHECK (scoring_strategy IN ('AVERAGE', 'MIN', 'MAX')),
    first_deadline       TIMESTAMPTZ  NOT NULL,
    second_deadline      TIMESTAMPTZ  NOT NULL,
    redistribution_factor INT         NOT NULL DEFAULT 2,
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_prc_criterion ON peer_review_configs(criterion_id);

CREATE TABLE peer_review_assignments (
    id                    UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    peer_review_config_id UUID        NOT NULL REFERENCES peer_review_configs(id) ON DELETE CASCADE,
    reviewer_user_id      UUID        REFERENCES users(id),
    reviewer_team_id      UUID        REFERENCES course_teams(id),
    reviewee_solution_id  UUID        NOT NULL REFERENCES solutions(id) ON DELETE CASCADE,
    round                 SMALLINT    NOT NULL DEFAULT 1 CHECK (round IN (1, 2)),
    status                VARCHAR(20) NOT NULL DEFAULT 'PENDING'
                              CHECK (status IN ('PENDING', 'COMPLETED', 'MISSED')),
    assigned_at           TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at          TIMESTAMPTZ,
    CONSTRAINT chk_reviewer_set CHECK (
        (reviewer_user_id IS NOT NULL AND reviewer_team_id IS NULL) OR
        (reviewer_user_id IS NULL AND reviewer_team_id IS NOT NULL)
    )
);

CREATE INDEX idx_pra_config        ON peer_review_assignments(peer_review_config_id);
CREATE INDEX idx_pra_reviewer_user ON peer_review_assignments(reviewer_user_id);
CREATE INDEX idx_pra_reviewee      ON peer_review_assignments(reviewee_solution_id);

CREATE TABLE peer_reviews (
    id            UUID         PRIMARY KEY DEFAULT gen_random_uuid(),
    assignment_id UUID         NOT NULL UNIQUE REFERENCES peer_review_assignments(id) ON DELETE CASCADE,
    grade         NUMERIC(10,2) NOT NULL,
    comment       TEXT,
    submitted_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_pr_assignment ON peer_reviews(assignment_id);
