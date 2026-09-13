alter table game_turn_reservation
    add column attack_result_request_id bigint;

alter table game_turn_reservation
    add column attack_result_json text;

alter table game_turn_reservation
    add constraint fk_game_turn_reservation_attack_result_request
        foreign key (attack_result_request_id) references game_mutation_request(id);

alter table game_turn_reservation
    add constraint ck_game_turn_reservation_attack_result_complete check (
        (attack_result_request_id is null and attack_result_json is null)
        or
        (attack_result_request_id is not null and attack_result_json is not null)
    );
