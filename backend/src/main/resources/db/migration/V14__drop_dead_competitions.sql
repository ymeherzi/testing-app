-- Competitions left over from an early experiment.
--
-- Major League Soccer, the Argentine Primera División and "Club Friendlies"
-- were once polled for live scores and have not been part of the game since.
-- They were invisible until competitions became a dropdown the player reads,
-- where they are simply confusing.
--
-- Deleted by what they are, not by name: a competition with no fixtures and
-- no provider reference is one nothing can ever add to. Anything still
-- carrying matches stays, whatever it is called.

delete from competitions c
where c.provider_ref is null
  and not exists (select 1 from matches m where m.competition_id = c.id);
