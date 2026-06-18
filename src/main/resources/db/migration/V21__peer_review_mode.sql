ALTER TABLE peer_review_configs
    ADD COLUMN IF NOT EXISTS review_mode VARCHAR(20) NOT NULL DEFAULT 'MANY_TO_ONE'
        CHECK (review_mode IN ('ONE_TO_ONE', 'MANY_TO_ONE'));
