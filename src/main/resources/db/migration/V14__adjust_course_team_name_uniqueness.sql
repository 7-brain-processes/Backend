ALTER TABLE course_teams
    DROP CONSTRAINT IF EXISTS course_teams_course_id_name_key;

CREATE UNIQUE INDEX IF NOT EXISTS uq_course_teams_course_name_without_post
    ON course_teams(course_id, name)
    WHERE post_id IS NULL;
