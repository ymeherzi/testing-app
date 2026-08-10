-- A "blank" gameweek: played and scored like any other, but kept out of the
-- season standings. Used for the warm-up round before the official start,
-- so newcomers can try the game without it counting against them.
alter table gameweeks add column counts_towards_table boolean not null default true;
