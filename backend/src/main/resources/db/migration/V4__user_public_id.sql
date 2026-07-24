-- Sequential user ids are internal only. Anything that appears in a URL or
-- an API payload uses this unguessable public id, so players cannot be
-- enumerated by counting.

alter table users add column public_id uuid not null default gen_random_uuid();

create unique index uq_users_public_id on users (public_id);
