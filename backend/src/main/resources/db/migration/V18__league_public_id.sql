-- Sequential league ids are internal only. A league that appears in a URL uses
-- this unguessable public id, so nobody can count the leagues of the game or
-- read the order they were created in from a shared link.
--
-- The old numeric links stop working: they only ever existed inside the app,
-- invitations travel as /join/CODE.

alter table leagues add column public_id uuid not null default gen_random_uuid();

create unique index uq_leagues_public_id on leagues (public_id);
