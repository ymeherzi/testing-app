-- Stable reference data: the six covered competitions.
-- provider_ref is the football-data.org v4 competition code.

insert into competitions (code, name, provider_ref)
values ('PL', 'Premier League', 'PL'),
       ('PD', 'La Liga', 'PD'),
       ('SA', 'Serie A', 'SA'),
       ('BL1', 'Bundesliga', 'BL1'),
       ('FL1', 'Ligue 1', 'FL1'),
       ('CL', 'UEFA Champions League', 'CL');
