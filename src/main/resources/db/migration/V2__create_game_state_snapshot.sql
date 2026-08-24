create table game_state_snapshot (
    session_id bigint primary key,
    state_json text not null,
    updated_at timestamp,
    constraint fk_game_state_snapshot_session
        foreign key (session_id) references game_session(id) on delete cascade
);
