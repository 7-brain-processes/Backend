CREATE TABLE post_captains (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    post_id UUID NOT NULL REFERENCES posts(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),

    UNIQUE(post_id, user_id)
);

CREATE INDEX idx_post_captains_post_id ON post_captains(post_id);
CREATE INDEX idx_post_captains_user_id ON post_captains(user_id);