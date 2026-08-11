-- The cups came back as championships.
--
-- V13 set domestic = false for these codes, and it worked on the rows that
-- existed that day. The cups are not among them: EspnCupImporter creates their
-- row on first import, which happened after the migration ran, and the column
-- defaults to true. So the favourite-championship dropdown offered the Coppa
-- Italia, the DFB-Pokal and the EFL Cup.
--
-- The importer now stamps the flag itself, on every import, which is what stops
-- this recurring. This repeats the correction for the rows already out there,
-- so the list is right before the next sync rather than after it.

update competitions set domestic = false
where code in ('CL', 'CSHIELD', 'TDC', 'FACUP', 'EFLCUP', 'CDR', 'COPPA', 'DFB', 'CDF');
