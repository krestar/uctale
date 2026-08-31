ALTER TABLE game_turn_reservation
    DROP CONSTRAINT ck_game_turn_reservation_attempt_count;

ALTER TABLE game_turn_reservation
    ALTER COLUMN attempt_count SET DEFAULT 0;

ALTER TABLE game_turn_reservation
    ADD CONSTRAINT ck_game_turn_reservation_attempt_count
        CHECK (attempt_count BETWEEN 0 AND 3);
