CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username        VARCHAR(50)  NOT NULL UNIQUE,
    password_hash   VARCHAR(255) NOT NULL,
    display_name    VARCHAR(100),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE courses (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name            VARCHAR(200) NOT NULL,
    description     VARCHAR(2000),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE course_members (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id       UUID        NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    user_id         UUID        NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    role            VARCHAR(10) NOT NULL CHECK (role IN ('TEACHER', 'STUDENT')),
    joined_at       TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (course_id, user_id)
);

CREATE INDEX idx_course_members_course ON course_members(course_id);
CREATE INDEX idx_course_members_user   ON course_members(user_id);

CREATE TABLE invites (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(32) NOT NULL UNIQUE,
    course_id       UUID        NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    role            VARCHAR(10) NOT NULL CHECK (role IN ('TEACHER', 'STUDENT')),
    expires_at      TIMESTAMPTZ,
    max_uses        INT,
    current_uses    INT         NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_invites_code ON invites(code);

CREATE TABLE posts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    course_id       UUID         NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
    author_id       UUID         NOT NULL REFERENCES users(id)   ON DELETE CASCADE,
    title           VARCHAR(300) NOT NULL,
    content         TEXT,
    type            VARCHAR(10)  NOT NULL CHECK (type IN ('MATERIAL', 'TASK')),
    deadline        TIMESTAMPTZ,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_posts_course ON posts(course_id);

CREATE TABLE post_files (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID         NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    original_name   VARCHAR(255) NOT NULL,
    content_type    VARCHAR(127) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    storage_path    VARCHAR(512) NOT NULL,
    uploaded_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_post_files_post ON post_files(post_id);

CREATE TABLE solutions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID        NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    student_id      UUID        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    text            TEXT,
    status          VARCHAR(10) NOT NULL DEFAULT 'SUBMITTED' CHECK (status IN ('SUBMITTED', 'GRADED')),
    grade           INT         CHECK (grade >= 0 AND grade <= 100),
    submitted_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    graded_at       TIMESTAMPTZ,
    UNIQUE (post_id, student_id)
);

CREATE INDEX idx_solutions_post    ON solutions(post_id);
CREATE INDEX idx_solutions_student ON solutions(student_id);

CREATE TABLE solution_files (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    solution_id     UUID         NOT NULL REFERENCES solutions(id) ON DELETE CASCADE,
    original_name   VARCHAR(255) NOT NULL,
    content_type    VARCHAR(127) NOT NULL,
    size_bytes      BIGINT       NOT NULL,
    storage_path    VARCHAR(512) NOT NULL,
    uploaded_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_solution_files_solution ON solution_files(solution_id);

CREATE TABLE comments (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id         UUID REFERENCES posts(id)     ON DELETE CASCADE,
    solution_id     UUID REFERENCES solutions(id)  ON DELETE CASCADE,
    author_id       UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    text            VARCHAR(5000) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CHECK (
        (post_id IS NOT NULL AND solution_id IS NULL) OR
        (post_id IS NULL AND solution_id IS NOT NULL)
    )
);

CREATE INDEX idx_comments_post     ON comments(post_id);
CREATE INDEX idx_comments_solution ON comments(solution_id);
