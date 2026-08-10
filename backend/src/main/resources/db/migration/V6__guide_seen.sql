-- Whether the player has been through (or skipped) the welcome guide.
-- Kept on the account rather than in the browser: skipping it on a phone
-- must not make it reappear on a laptop.
alter table users add column guide_seen boolean not null default false;

-- Existing accounts have been playing for weeks; only newcomers need it.
update users set guide_seen = true;
