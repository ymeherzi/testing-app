-- The launch reset (V8) removed the offline generator's clubs by their
-- provider prefix, which left behind an older batch: real clubs created
-- during an early experiment with a live-score feed, spanning leagues the
-- game does not cover (Argentina, MLS, Turkey). They would show up in the
-- "favourite club" picker for a game about the big European leagues.
--
-- Referential rather than pattern-based on purpose: whatever the real
-- provider has already created is attached to a match and survives, so this
-- is safe whichever order the sync and this migration run in.
update users set favourite_club_team_id = null
where favourite_club_team_id in (
    select t.id from teams t
    where not exists (select 1 from matches m where m.home_team_id = t.id or m.away_team_id = t.id)
);

delete from teams t
where not exists (select 1 from matches m where m.home_team_id = t.id or m.away_team_id = t.id);
