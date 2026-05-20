CREATE TABLE grading_config_versions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID           NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    version_number  INT            NOT NULL,
    max_grade       NUMERIC(10, 2) NOT NULL,
    modifiers_json  TEXT,
    created_at      TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (post_id, version_number)
);

CREATE INDEX idx_grading_config_versions_post ON grading_config_versions(post_id);

CREATE TABLE versioned_criteria (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    config_version_id UUID           NOT NULL REFERENCES grading_config_versions(id) ON DELETE CASCADE,
    type              VARCHAR(20)    NOT NULL CHECK (type IN ('YES_NO', 'PERCENTAGE', 'POINTS')),
    title             VARCHAR(300)   NOT NULL,
    max_points        NUMERIC(10, 2) NOT NULL,
    weight            NUMERIC(10, 2) NOT NULL DEFAULT 1.0,
    sort_order        INT            NOT NULL DEFAULT 0
);

CREATE INDEX idx_versioned_criteria_config_version ON versioned_criteria(config_version_id);

CREATE TABLE assessment_results (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    solution_id       UUID           NOT NULL REFERENCES solutions(id) ON DELETE CASCADE,
    config_version_id UUID           NOT NULL REFERENCES grading_config_versions(id),
    basic_score       NUMERIC(10, 2),
    modifier_delta    NUMERIC(10, 2) NOT NULL DEFAULT 0,
    final_score       NUMERIC(10, 2),
    is_published      BOOLEAN        NOT NULL DEFAULT FALSE,
    graded_at         TIMESTAMPTZ,
    created_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (solution_id)
);

CREATE INDEX idx_assessment_results_solution ON assessment_results(solution_id);
CREATE INDEX idx_assessment_results_config_version ON assessment_results(config_version_id);

CREATE TABLE assessment_criterion_grades (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    assessment_result_id UUID           NOT NULL REFERENCES assessment_results(id) ON DELETE CASCADE,
    versioned_criterion_id UUID         NOT NULL REFERENCES versioned_criteria(id),
    value                NUMERIC(10, 2) NOT NULL,
    comment              VARCHAR(2000),
    created_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ    NOT NULL DEFAULT now(),
    UNIQUE (assessment_result_id, versioned_criterion_id)
);

CREATE INDEX idx_assessment_criterion_grades_result ON assessment_criterion_grades(assessment_result_id);
CREATE INDEX idx_assessment_criterion_grades_criterion ON assessment_criterion_grades(versioned_criterion_id);

-- Migrate existing grading configs into version 1 snapshots
INSERT INTO grading_config_versions (id, post_id, version_number, max_grade, modifiers_json, created_at)
SELECT gen_random_uuid(), gc.post_id, 1, gc.max_grade, gc.modifiers_json, gc.created_at
FROM grading_configs gc;

-- Migrate existing criteria into versioned criteria
INSERT INTO versioned_criteria (id, config_version_id, type, title, max_points, weight, sort_order)
SELECT gen_random_uuid(), gcv.id, c.type, c.title, c.max_points, c.weight, c.sort_order
FROM criteria c
JOIN grading_configs gc ON gc.id = c.grading_config_id
JOIN grading_config_versions gcv ON gcv.post_id = gc.post_id AND gcv.version_number = 1;

-- Migrate existing criteria grades into assessment results
INSERT INTO assessment_results (id, solution_id, config_version_id, is_published, graded_at, created_at, updated_at)
SELECT gen_random_uuid(), cg.solution_id, gcv.id, gc.results_visible, MAX(cg.updated_at), MIN(cg.created_at), MAX(cg.updated_at)
FROM criteria_grades cg
JOIN criteria c ON c.id = cg.criterion_id
JOIN grading_configs gc ON gc.id = c.grading_config_id
JOIN grading_config_versions gcv ON gcv.post_id = gc.post_id AND gcv.version_number = 1
GROUP BY cg.solution_id, gcv.id, gc.results_visible;

-- Migrate existing criteria grades into assessment criterion grades
INSERT INTO assessment_criterion_grades (id, assessment_result_id, versioned_criterion_id, value, comment, created_at, updated_at)
SELECT gen_random_uuid(), ar.id, vc.id, cg.value, cg.comment, cg.created_at, cg.updated_at
FROM criteria_grades cg
JOIN criteria c ON c.id = cg.criterion_id
JOIN grading_configs gc ON gc.id = c.grading_config_id
JOIN grading_config_versions gcv ON gcv.post_id = gc.post_id AND gcv.version_number = 1
JOIN versioned_criteria vc ON vc.config_version_id = gcv.id
    AND vc.title = c.title
    AND vc.type = c.type
    AND vc.max_points = c.max_points
    AND vc.sort_order = c.sort_order
JOIN assessment_results ar ON ar.solution_id = cg.solution_id AND ar.config_version_id = gcv.id;

-- Drop the old criteria grades table (data has been migrated)
DROP TABLE criteria_grades;
