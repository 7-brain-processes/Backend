CREATE TYPE invitation_status AS ENUM ('PENDING', 'ACCEPTED', 'DECLINED');

CREATE TABLE team_invitations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    captain_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    student_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    status invitation_status NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    responded_at TIMESTAMP WITH TIME ZONE,

    UNIQUE(captain_id, student_id, post_id)
);

CREATE INDEX idx_team_invitations_captain_id ON team_invitations(captain_id);
CREATE INDEX idx_team_invitations_student_id ON team_invitations(student_id);
CREATE INDEX idx_team_invitations_post_id ON team_invitations(post_id);
CREATE INDEX idx_team_invitations_status ON team_invitations(status);