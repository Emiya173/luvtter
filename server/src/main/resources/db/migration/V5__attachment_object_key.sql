ALTER TABLE letter_attachments
    ADD COLUMN object_key TEXT;

CREATE INDEX IF NOT EXISTS idx_letter_attachments_object_key
    ON letter_attachments(object_key) WHERE object_key IS NOT NULL;
