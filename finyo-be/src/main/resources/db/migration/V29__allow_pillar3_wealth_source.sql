ALTER TABLE wealth_bucket DROP CONSTRAINT wealth_bucket_source_check;
ALTER TABLE wealth_bucket ADD CONSTRAINT wealth_bucket_source_check
    CHECK (source IN ('MANUAL', 'PORTFOLIO', 'PILLAR3'));
