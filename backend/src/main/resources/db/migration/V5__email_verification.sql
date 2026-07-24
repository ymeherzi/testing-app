-- Proving an email address exists (codes), remembering devices that have
-- already passed a check, and letting Google vouch for an address instead.

alter table users add column email_verified boolean not null default false;
alter table users add column google_subject varchar(64) unique;
-- password is optional for Google-only accounts
alter table users alter column password_hash drop not null;

create table auth_codes (
    id          bigint      generated always as identity primary key,
    user_id     bigint      not null references users (id) on delete cascade,
    purpose     varchar(16) not null, -- VERIFY_EMAIL | NEW_DEVICE
    code_hash   varchar(100) not null,
    attempts    int         not null default 0,
    expires_at  timestamptz not null,
    consumed_at timestamptz,
    created_at  timestamptz not null default now()
);

create index idx_auth_codes_user_purpose on auth_codes (user_id, purpose);

create table trusted_devices (
    id           bigint      generated always as identity primary key,
    user_id      bigint      not null references users (id) on delete cascade,
    token_hash   varchar(64) not null unique,
    label        varchar(120),
    last_seen_at timestamptz not null default now(),
    expires_at   timestamptz not null,
    created_at   timestamptz not null default now()
);

create index idx_trusted_devices_user on trusted_devices (user_id);

-- Accounts that predate verification keep working: they are already trusted.
update users set email_verified = true;
