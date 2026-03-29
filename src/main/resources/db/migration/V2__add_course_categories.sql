CREATE TABLE course_categories (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id   UUID NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    title       VARCHAR(200) NOT NULL,
    description VARCHAR(2000),
    active      BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_course_categories_course ON course_categories(course_id);

ALTER TABLE course_members
    ADD COLUMN category_id UUID REFERENCES course_categories(id) ON DELETE SET NULL;

CREATE INDEX idx_course_members_category ON course_members(category_id);
