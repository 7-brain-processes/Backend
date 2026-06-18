-- Team peer-review penalties store the offending team in team_id, so user_id must be nullable.
ALTER TABLE peer_review_penalties ALTER COLUMN user_id DROP NOT NULL;
