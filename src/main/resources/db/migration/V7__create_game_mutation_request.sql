CREATE TABLE game_mutation_request (
    id BIGSERIAL PRIMARY KEY,
    owner_key VARCHAR(64) NOT NULL,
    operation VARCHAR(16) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    session_id BIGINT,
    expected_turn INTEGER,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(16) NOT NULL,
    result_session_id BIGINT,
    result_turn INTEGER,
    result_title VARCHAR(200),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_game_mutation_owner_operation_key UNIQUE (owner_key, operation, idempotency_key),
    CONSTRAINT ck_game_mutation_status CHECK (status IN ('PROCESSING', 'COMPLETED', 'FAILED'))
);

CREATE INDEX idx_game_mutation_created_at ON game_mutation_request (created_at);
