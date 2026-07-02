-- Core schema for milestone 1: users, catalog (competitions/teams/matches),
-- gameweeks with curated fixtures, and predictions.
-- Public leagues are dynamic views over users.country / users.favourite_club_team_id
-- (see docs/DESIGN.md §5.1) — no league tables needed yet.

create table competitions (
    id           bigint generated always as identity primary key,
    code         varchar(8)  not null unique,
    name         varchar(64) not null,
    provider_ref varchar(32) unique
);

create table teams (
    id           bigint generated always as identity primary key,
    name         varchar(128) not null,
    short_name   varchar(64),
    crest_url    varchar(512),
    provider_ref varchar(64) unique
);

create table matches (
    id             bigint generated always as identity primary key,
    competition_id bigint      not null references competitions (id),
    home_team_id   bigint      not null references teams (id),
    away_team_id   bigint      not null references teams (id),
    kickoff_utc    timestamptz not null,
    status         varchar(16) not null default 'SCHEDULED',
    home_score     integer,
    away_score     integer,
    provider_ref   varchar(64) unique,
    last_synced_at timestamptz,
    constraint chk_matches_teams_differ check (home_team_id <> away_team_id)
);

create index idx_matches_status_kickoff on matches (status, kickoff_utc);

create table users (
    id                     bigint generated always as identity primary key,
    email                  varchar(255) not null,
    password_hash          varchar(100) not null,
    display_name           varchar(50)  not null,
    country                varchar(2),
    favourite_club_team_id bigint references teams (id),
    is_admin               boolean      not null default false,
    created_at             timestamptz  not null default now()
);

create unique index uq_users_email on users (lower(email));

create table gameweeks (
    id           bigint generated always as identity primary key,
    season       varchar(9)  not null,
    week_index   int         not null,
    type         varchar(8)  not null, -- WEEKEND | MIDWEEK
    status       varchar(10) not null default 'DRAFT', -- DRAFT | PUBLISHED | SCORED
    window_start timestamptz not null,
    window_end   timestamptz not null,
    unique (season, week_index)
);

create table gameweek_fixtures (
    id          bigint generated always as identity primary key,
    gameweek_id bigint not null references gameweeks (id) on delete cascade,
    match_id    bigint not null references matches (id),
    unique (gameweek_id, match_id)
);

create table predictions (
    id                  bigint generated always as identity primary key,
    user_id             bigint      not null references users (id),
    gameweek_fixture_id bigint      not null references gameweek_fixtures (id) on delete cascade,
    home_goals          integer     not null check (home_goals between 0 and 20),
    away_goals          integer     not null check (away_goals between 0 and 20),
    points              integer, -- null until the match is scored
    scored_at           timestamptz,
    updated_at          timestamptz not null default now(),
    unique (user_id, gameweek_fixture_id)
);

create index idx_predictions_fixture on predictions (gameweek_fixture_id);
