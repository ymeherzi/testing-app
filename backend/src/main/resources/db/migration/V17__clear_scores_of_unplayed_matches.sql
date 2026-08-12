-- Matches that carry a score they never earned.
--
-- ESPN answers "0" for the score of a fixture that has not kicked off, and the
-- cup importer stored it. Every cup fixture in the pool therefore looked like a
-- goalless draw days ahead: the card showed "0 : 0" once the match locked, and
-- offered players a running points total before a ball was kicked.
--
-- The importer and the live poll now only record a score for a match that has
-- started. This clears what they wrote before that.
--
-- Written as what it is rather than by provider: a match that is not over has
-- no result, whoever put one there.

update matches
set home_score = null,
    away_score = null
where status not in ('FINISHED', 'AWARDED')
  and (home_score is not null or away_score is not null);
