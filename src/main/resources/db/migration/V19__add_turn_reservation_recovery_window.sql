ALTER TABLE game_turn_reservation
    ADD COLUMN recovery_available_at TIMESTAMP NULL;

CREATE INDEX idx_game_turn_reservation_recovery_available
    ON game_turn_reservation (recovery_available_at);

-- V10까지의 영구 소진 row는 배포 후 즉시 새 recovery window를 획득할 수 있게 한다.
-- canonical game state를 변경하지 않고 실행 제어 메타데이터만 명시적으로 승격한다.
UPDATE game_turn_reservation
SET recovery_available_at = CURRENT_TIMESTAMP
WHERE provider_attempt_count >= 3;
