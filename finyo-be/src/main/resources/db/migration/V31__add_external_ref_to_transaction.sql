-- Bank-side transaction reference (camt.053 AcctSvcrRef / EndToEndId) used
-- for exact duplicate detection on re-imported statements. Manual and legacy
-- CSV rows have no reference, so the uniqueness is partial: only rows that
-- carry a reference must be unique per user.
ALTER TABLE transaction ADD COLUMN external_ref VARCHAR(255);

CREATE UNIQUE INDEX uq_transaction_user_external_ref
    ON transaction(user_id, external_ref) WHERE external_ref IS NOT NULL;
