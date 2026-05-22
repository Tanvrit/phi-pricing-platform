-- Add lifecycle column to plans (LIVE / DRAFT / RETIRED). Defaults to LIVE so
-- existing rows keep their semantics (everything imported pre-V4 is live).
ALTER TABLE plans ADD COLUMN IF NOT EXISTS lifecycle VARCHAR(20) NOT NULL DEFAULT 'LIVE';
CREATE INDEX IF NOT EXISTS idx_plans_lifecycle ON plans (lifecycle);
