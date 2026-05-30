ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS repeat_count INTEGER;
