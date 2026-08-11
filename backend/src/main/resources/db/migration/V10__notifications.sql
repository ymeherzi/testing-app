-- Telling players a round is open, and reminding those who haven't played.
--
-- Nobody is notified today, so a round can open and go unnoticed until
-- someone happens to visit the site.

alter table users add column notify_email boolean not null default true;

create table notifications (
    id          bigint      generated always as identity primary key,
    user_id     bigint      not null references users (id) on delete cascade,
    gameweek_id bigint      not null references gameweeks (id) on delete cascade,
    kind        varchar(16) not null, -- ROUND_OPENED | KICKOFF_REMINDER
    sent_at     timestamptz not null default now()
);

-- The unique index is the guarantee, not the code around it: a retry, two
-- schedulers or a restart mid-batch can all replay a send, and being mailed
-- twice about the same round is the fastest way to be marked as spam.
create unique index uq_notifications_user_gameweek_kind
    on notifications (user_id, gameweek_id, kind);

-- "how many have I sent in the last day", the free-tier budget check
create index idx_notifications_sent_at on notifications (sent_at);
