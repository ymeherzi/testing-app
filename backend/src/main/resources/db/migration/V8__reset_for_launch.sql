-- Clears the demo season so the real one can start on 21 August 2026.
--
-- Accounts and private leagues survive: players keep their logins, their
-- groups and their invite codes. What goes is everything that made up the
-- practice season — predictions, points, gameweeks and the fixtures the
-- offline generator invented.
--
-- On a fresh database this is a no-op; on production it runs exactly once.

-- Members point at the gameweek they joined in, which is about to be
-- deleted. Null means "counts from the start", which is what a member of a
-- league joining a brand new season should have anyway.
update league_members set join_gameweek_id = null;

delete from predictions;
delete from gameweek_fixtures;
delete from gameweeks;
delete from matches;

-- The demo clubs disappear with the demo season, so anyone who picked one as
-- their favourite loses that choice rather than pointing at a deleted row.
-- They pick again from the real list on their next visit.
update users set favourite_club_team_id = null
where favourite_club_team_id in (select id from teams where provider_ref like 'seed:%');

delete from teams where provider_ref like 'seed:%';

-- competitions are reference data (V2) and stay: the real provider keys on
-- the same codes (PL, PD, SA, BL1, FL1, CL).
