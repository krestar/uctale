ALTER TABLE game_log
    ADD COLUMN canonical_result_id VARCHAR(128);

ALTER TABLE game_log
    ADD COLUMN generated_story_id VARCHAR(128);

ALTER TABLE game_log
    ADD CONSTRAINT ck_game_log_narrative_link_pair
    CHECK (
        (canonical_result_id IS NULL AND generated_story_id IS NULL)
        OR (canonical_result_id IS NOT NULL AND generated_story_id IS NOT NULL)
    );
