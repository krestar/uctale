-- Stable, owner-scoped keyset page order. PostgreSQL DESC NULLS LAST must match the query.
CREATE INDEX idx_game_session_owner_updated_id
    ON game_session(owner_key, updated_at DESC NULLS LAST, id DESC);

-- Latest completed title and latest in-flight progress request, with deterministic id tie-break.
CREATE INDEX idx_mutation_session_title_lookup
    ON game_mutation_request(owner_key, result_session_id, result_turn, status, updated_at DESC, id DESC);
CREATE INDEX idx_mutation_session_progress_lookup
    ON game_mutation_request(owner_key, session_id, expected_turn, operation, updated_at DESC, id DESC);
