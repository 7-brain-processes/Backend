CREATE TABLE grading_configs (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID           NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    max_grade       NUMERIC(10, 2) NOT NULL,
    results_visible BOOLEAN        NOT NULL DEFAULT FALSE,
    modifiers_json  TEXT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (post_id)
);

CREATE INDEX idx_grading_configs_post ON grading_configs(post_id);

CREATE TABLE criteria (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    grading_config_id UUID           NOT NULL REFERENCES grading_configs(id) ON DELETE CASCADE,
    type              VARCHAR(20)    NOT NULL CHECK (type IN ('YES_NO', 'PERCENTAGE', 'POINTS')),
    title             VARCHAR(300)   NOT NULL,
    max_points        NUMERIC(10, 2) NOT NULL,
    weight            NUMERIC(10, 2) NOT NULL DEFAULT 1.0,
    sort_order        INT            NOT NULL DEFAULT 0
);

CREATE INDEX idx_criteria_grading_config ON criteria(grading_config_id);

CREATE TABLE criteria_grades (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    solution_id  UUID           NOT NULL REFERENCES solutions(id) ON DELETE CASCADE,
    criterion_id UUID           NOT NULL REFERENCES criteria(id) ON DELETE CASCADE,
    value        NUMERIC(10, 2) NOT NULL,
    comment      VARCHAR(2000),
    created_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at   TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (solution_id, criterion_id)
);

CREATE INDEX idx_criteria_grades_solution   ON criteria_grades(solution_id);
CREATE INDEX idx_criteria_grades_criterion  ON criteria_grades(criterion_id);
