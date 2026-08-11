-- A favourite championship, and the public table that follows from it.
--
-- Deliberately independent of the favourite club: a relegation would
-- otherwise move a player from one league to another, taking their points
-- with them, and an Arsenal supporter is perfectly entitled to follow Serie A.

alter table users add column favourite_competition_id bigint references competitions (id);

create index idx_users_favourite_competition on users (favourite_competition_id);
