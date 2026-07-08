-- Private points leagues (design §5.2/§5.3). Public leagues remain dynamic
-- views over user attributes and need no tables.

create table leagues (
    id            bigint generated always as identity primary key,
    name          varchar(60) not null,
    invite_code   varchar(12) not null unique,
    admin_user_id bigint      not null references users (id),
    max_members   int         not null default 50,
    created_at    timestamptz not null default now()
);

create table league_members (
    id               bigint      generated always as identity primary key,
    league_id        bigint      not null references leagues (id) on delete cascade,
    user_id          bigint      not null references users (id),
    -- gameweek that was current/next at join; NULL = count all gameweeks
    join_gameweek_id bigint      references gameweeks (id),
    joined_at        timestamptz not null default now(),
    unique (league_id, user_id)
);

create index idx_league_members_user on league_members (user_id);
