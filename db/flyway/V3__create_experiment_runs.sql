-- Experiment runs durable store (Phase 5: DB-backed runs).
-- Backs ExperimentRunEntity; legacy run manifests are imported idempotently at startup.
CREATE TABLE IF NOT EXISTS experiment_runs (
    id            VARCHAR(36) PRIMARY KEY,
    template_id   VARCHAR(128) NOT NULL,
    state         VARCHAR(32)  NOT NULL,
    started_at    TIMESTAMPTZ,
    duration_ms   BIGINT       NOT NULL DEFAULT 0,
    labels        TEXT,
    parameters    TEXT,
    metrics       TEXT,
    app_commit    VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_experiment_runs_template ON experiment_runs (template_id);
CREATE INDEX IF NOT EXISTS idx_experiment_runs_state ON experiment_runs (state);
CREATE INDEX IF NOT EXISTS idx_experiment_runs_started ON experiment_runs (started_at DESC);
