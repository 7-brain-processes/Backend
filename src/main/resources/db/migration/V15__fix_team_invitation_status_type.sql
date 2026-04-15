ALTER TABLE team_invitations
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE team_invitations
    ALTER COLUMN status TYPE VARCHAR(10)
    USING status::text;

ALTER TABLE team_invitations
    ALTER COLUMN status SET DEFAULT 'PENDING';

DROP TYPE IF EXISTS invitation_status;
