-- Add optimistic locking and persisted artifact metadata to the run store.
ALTER TABLE experiment_runs
    ADD COLUMN IF NOT EXISTS artifacts TEXT,
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 0;

CREATE INDEX IF NOT EXISTS idx_experiment_runs_template_started
    ON experiment_runs (template_id, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_experiment_runs_state_started
    ON experiment_runs (state, started_at DESC);
