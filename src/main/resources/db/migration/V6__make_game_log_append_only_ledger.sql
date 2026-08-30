alter table game_log
    add column input_choice_id integer;

alter table game_log
    add column input_choice_text varchar(255);

alter table game_log
    add column previous_state_version integer;

alter table game_log
    add column state_version integer;

update game_log
set input_choice_text = (
    select previous_log.user_choice
    from game_log previous_log
    where previous_log.session_id = game_log.session_id
      and previous_log.turn_number = game_log.turn_number - 1
);

update game_log
set previous_state_version = case
    when turn_number = 1 then 0
    else turn_number - 1
end,
    state_version = turn_number;

alter table game_log
    alter column previous_state_version set not null;

alter table game_log
    alter column state_version set not null;

alter table game_log
    drop column user_choice;
