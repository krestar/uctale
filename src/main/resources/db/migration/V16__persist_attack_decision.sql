alter table game_turn_reservation
    add column attack_result_request_id bigint;

alter table game_turn_reservation
    add column attack_result_json text;
