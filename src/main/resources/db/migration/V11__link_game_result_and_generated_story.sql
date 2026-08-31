ALTER TABLE game_log
    ADD COLUMN canonical_result_id VARCHAR(128),
    ADD COLUMN generated_story_id VARCHAR(128);
