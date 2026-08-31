ALTER TABLE game_turn_reservation
    ADD COLUMN provider_attempt_count INTEGER NOT NULL DEFAULT 0;

ALTER TABLE game_turn_reservation
    ADD CONSTRAINT ck_game_turn_reservation_provider_attempt_count
        CHECK (provider_attempt_count BETWEEN 0 AND 3);
