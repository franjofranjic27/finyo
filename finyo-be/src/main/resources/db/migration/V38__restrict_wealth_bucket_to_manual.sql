-- Auto-mirrored wealth rows: the overview now synthesizes read-only portfolio
-- and pillar 3a rows live from their modules on every read, so persisted
-- PORTFOLIO/PILLAR3 buckets are obsolete. Deleting them is intended data loss:
-- their balances were always derived at read time (never stored in this table),
-- and the synthesized auto rows replace them one-to-one.
DELETE FROM wealth_bucket WHERE source <> 'MANUAL';

ALTER TABLE wealth_bucket DROP CONSTRAINT wealth_bucket_source_check;
ALTER TABLE wealth_bucket ADD CONSTRAINT wealth_bucket_source_check
    CHECK (source = 'MANUAL');
