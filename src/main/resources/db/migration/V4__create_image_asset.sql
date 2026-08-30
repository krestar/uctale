create table image_asset (
    id varchar(36) primary key,
    session_id bigint not null,
    turn_number integer not null,
    prompt text not null,
    aspect_ratio varchar(8) not null,
    content_type varchar(100),
    image_bytes bytea,
    created_at timestamp,
    generated_at timestamp,
    constraint fk_image_asset_session
        foreign key (session_id) references game_session(id) on delete cascade,
    constraint uk_image_asset_session_turn
        unique (session_id, turn_number)
);

create index idx_image_asset_session_owner_lookup
    on image_asset(session_id, id);
