CREATE TABLE lambdas
(
    id                         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name                       VARCHAR(255) UNIQUE NOT NULL,
    description                TEXT,
    callback_url               VARCHAR(500) NOT NULL,
    owner_team                 VARCHAR(100),
    status                     VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    gate_action                VARCHAR(50),
    enqueue_timeout_seconds    INTEGER NOT NULL DEFAULT 300,
    claim_timeout_seconds      INTEGER NOT NULL DEFAULT 60,
    heartbeat_interval_seconds INTEGER NOT NULL DEFAULT 10,
    heartbeat_timeout_seconds  INTEGER NOT NULL DEFAULT 60,
    max_attempts               INTEGER NOT NULL DEFAULT 3,
    max_delivery_count         INTEGER NOT NULL DEFAULT 5,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE collections (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lambda_name VARCHAR(100) NOT NULL REFERENCES lambdas(name),
    name VARCHAR(255) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    gate_action VARCHAR(50),
    UNIQUE(lambda_name, name)
);

CREATE TABLE tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    lambda_name VARCHAR(255) NOT NULL REFERENCES lambdas(name),
    collection_name VARCHAR(255),
    priority VARCHAR(50) NOT NULL DEFAULT 'MEDIUM',
    status VARCHAR(50) NOT NULL DEFAULT 'NEW',
    payload JSONB NOT NULL DEFAULT '{}',
    result JSONB,
    next_trigger_at TIMESTAMP WITH TIME ZONE NOT NULL,
    scheduled_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    attempt_count INT NOT NULL DEFAULT 0,
    claimed_by VARCHAR(255),
    last_heart_beat TIMESTAMP WITH TIME ZONE,
    error_message TEXT,
    http_status_code INT,

    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    CONSTRAINT fk_tasks_lambda FOREIGN KEY (lambda_name) REFERENCES lambdas(name)
);

CREATE INDEX idx_tasks_store_consumer
    ON tasks (next_trigger_at)
    WHERE status NOT IN ('SUCCESS', 'FATAL_FAILURE');

CREATE INDEX idx_tasks_lambda_status
    ON tasks (lambda_name, status, created_at DESC);