alter table game_turn_reservation
    add column skill_check_request_id bigint;

alter table game_turn_reservation
    add column skill_check_stat_type varchar(32);

alter table game_turn_reservation
    add column skill_check_raw_roll integer;

alter table game_turn_reservation
    add column skill_check_stat_modifier integer;

alter table game_turn_reservation
    add column skill_check_situational_modifier integer;

alter table game_turn_reservation
    add column skill_check_dc integer;

alter table game_turn_reservation
    add column skill_check_total integer;

alter table game_turn_reservation
    add column skill_check_outcome varchar(16);

alter table game_turn_reservation
    add column skill_check_ruleset_version integer;

alter table game_turn_reservation
    add constraint fk_game_turn_reservation_skill_check_request
        foreign key (skill_check_request_id) references game_mutation_request(id);

alter table game_turn_reservation
    add constraint ck_game_turn_reservation_skill_check_complete check (
        (skill_check_request_id is null
            and skill_check_stat_type is null
            and skill_check_raw_roll is null
            and skill_check_stat_modifier is null
            and skill_check_situational_modifier is null
            and skill_check_dc is null
            and skill_check_total is null
            and skill_check_outcome is null
            and skill_check_ruleset_version is null)
        or
        (skill_check_request_id is not null
            and skill_check_stat_type is not null
            and skill_check_raw_roll is not null
            and skill_check_stat_modifier is not null
            and skill_check_situational_modifier is not null
            and skill_check_dc is not null
            and skill_check_total is not null
            and skill_check_outcome is not null
            and skill_check_ruleset_version is not null)
    );

alter table game_log
    add column skill_check_stat_type varchar(32);

alter table game_log
    add column skill_check_raw_roll integer;

alter table game_log
    add column skill_check_stat_modifier integer;

alter table game_log
    add column skill_check_situational_modifier integer;

alter table game_log
    add column skill_check_dc integer;

alter table game_log
    add column skill_check_total integer;

alter table game_log
    add column skill_check_outcome varchar(16);

alter table game_log
    add column skill_check_ruleset_version integer;

alter table game_log
    add constraint ck_game_log_skill_check_complete check (
        (skill_check_stat_type is null
            and skill_check_raw_roll is null
            and skill_check_stat_modifier is null
            and skill_check_situational_modifier is null
            and skill_check_dc is null
            and skill_check_total is null
            and skill_check_outcome is null
            and skill_check_ruleset_version is null)
        or
        (skill_check_stat_type is not null
            and skill_check_raw_roll is not null
            and skill_check_stat_modifier is not null
            and skill_check_situational_modifier is not null
            and skill_check_dc is not null
            and skill_check_total is not null
            and skill_check_outcome is not null
            and skill_check_ruleset_version is not null)
    );
