-- The gross salary can now be entered as a monthly or a yearly amount; the
-- counterpart is derived on upsert. Existing rows were entered monthly, so
-- the yearly gross is backfilled as gross_monthly x 12 (x 13 with a 13th
-- salary), matching the normalization in SalaryService.
ALTER TABLE salary_profile
    ADD COLUMN input_mode VARCHAR(10) NOT NULL DEFAULT 'MONTHLY' CHECK (input_mode IN ('MONTHLY','YEARLY')),
    ADD COLUMN gross_yearly DECIMAL(19,4) NOT NULL DEFAULT 0 CHECK (gross_yearly >= 0);

UPDATE salary_profile
SET gross_yearly = gross_monthly * (CASE WHEN thirteenth_salary THEN 13 ELSE 12 END);
