-- More to choose from than the big five leagues.
--
-- These three are on football-data's free tier, so they only need a row with
-- a provider reference for the sync to start pulling them. The cups and the
-- Belgian and Scottish leagues are not on the free tier at all; they come
-- from ESPN and their rows are created on first import.

insert into competitions (code, name, provider_ref)
values ('ELC', 'Championship', 'ELC'),
       ('DED', 'Eredivisie', 'DED'),
       ('PPL', 'Primeira Liga', 'PPL')
on conflict (code) do nothing;
