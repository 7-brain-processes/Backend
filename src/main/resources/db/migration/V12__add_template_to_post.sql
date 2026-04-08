ALTER TABLE posts
    ADD COLUMN team_requirement_template_id UUID REFERENCES team_requirement_templates(id) ON DELETE SET NULL;

CREATE INDEX idx_posts_team_requirement_template_id ON posts(team_requirement_template_id);
