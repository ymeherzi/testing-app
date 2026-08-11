-- Choosing a favourite club out of several hundred.
--
-- The catalogue now spans nine leagues and eight cups, so the club list has
-- outgrown a plain dropdown. These three columns are what a search and a
-- competition filter need.

-- The three-letter abbreviation football-data already returns and the import
-- was throwing away. Without it "PSG" matches nothing, because those letters
-- appear nowhere in "Paris Saint-Germain FC".
alter table teams add column tla varchar(8);

-- Which league a club plays in, stamped from that league's own squad list.
-- Null is a normal state: clubs that reached us through a cup tie or through
-- ESPN have no such list, and are still perfectly choosable.
alter table teams add column primary_competition_id bigint references competitions (id);

create index idx_teams_primary_competition on teams (primary_competition_id);

-- Whether a competition is a domestic league.
--
-- Two callers need it, and both would otherwise get it wrong. Stamping a
-- club's league must ignore the Champions League and the cups: their squad
-- lists are full of clubs that already belong to a domestic league, and Real
-- Madrid would come out as a Champions League club. And "my favourite
-- championship" must not offer the Coupe de France — nobody supports a cup.
alter table competitions add column domestic boolean not null default true;

update competitions set domestic = false
where code in ('CL', 'CSHIELD', 'TDC', 'FACUP', 'EFLCUP', 'CDR', 'COPPA', 'DFB', 'CDF');
