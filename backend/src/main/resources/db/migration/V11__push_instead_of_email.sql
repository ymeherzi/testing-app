-- Round notifications move from email to web push.
--
-- V10 shipped them as email and is already applied in production, so it is
-- undone here rather than rewritten: a migration that has run cannot be
-- edited without Flyway refusing to start.

-- Consent is now the existence of a push subscription, not a column.
alter table users drop column notify_email;

-- The season-launch announcement belongs to no particular round.
alter table notifications alter column gameweek_id drop not null;

-- The unique index on (user_id, gameweek_id, kind) does not constrain rows
-- whose gameweek is null — in SQL, null never equals null — so the
-- once-and-only-once guarantee needs its own index for those.
create unique index uq_notifications_user_kind_no_gameweek
    on notifications (user_id, kind) where gameweek_id is null;

create table push_subscriptions (
    id           bigint      generated always as identity primary key,
    user_id      bigint      not null references users (id) on delete cascade,
    -- the push service's URL for this device; it identifies the subscription
    endpoint     text        not null unique,
    -- the browser's public key and auth secret, base64url as the browser gives them
    p256dh       text        not null,
    auth         text        not null,
    created_at   timestamptz not null default now(),
    last_seen_at timestamptz not null default now()
);

create index idx_push_subscriptions_user on push_subscriptions (user_id);
