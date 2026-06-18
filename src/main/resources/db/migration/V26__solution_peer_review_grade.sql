ALTER TABLE solutions
    ADD COLUMN IF NOT EXISTS peer_review_grade NUMERIC(10, 2);
