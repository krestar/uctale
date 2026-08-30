alter table game_session
    add column owner_key varchar(64);

update game_session
set owner_key = concat('legacy-', id)
where owner_key is null;

alter table game_session
    alter column owner_key set not null;

create index idx_game_session_owner_id
    on game_session(owner_key, id);
