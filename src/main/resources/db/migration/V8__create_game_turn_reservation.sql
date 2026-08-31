CREATE TABLE game_turn_reservation (
    session_id BIGINT NOT NULL,
    expected_turn INTEGER NOT NULL,
    request_id BIGINT NOT NULL,
    lease_owner VARCHAR(36) NOT NULL,
    lease_expires_at TIMESTAMP NOT NULL,
    attempt_count INTEGER NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, expected_turn),
    CONSTRAINT fk_game_turn_reservation_request
        FOREIGN KEY (request_id) REFERENCES game_mutation_request(id),
    CONSTRAINT ck_game_turn_reservation_attempt_count
        CHECK (attempt_count BETWEEN 1 AND 3)
);

CREATE INDEX idx_game_turn_reservation_lease_expiry
    ON game_turn_reservation (lease_expires_at);
