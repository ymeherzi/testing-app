# Football Prediction Game — v1 Design

*Status: draft v1 — product design agreed through Q&A on 2026-07-01. No code yet.*

## 1. Vision

A fun, season-long football score-prediction game. Players predict scorelines
for a curated set of big matches across Europe each gameweek, earn points on a
tiered accuracy scale, and compete in public and private leagues over the
season — including FPL-style head-to-head leagues.

Mobile-first, fluid, installable web app (PWA). Built for friends, family and
colleagues first; public leagues give every player a global context from day one.

## 2. Game rules

### 2.1 Scoring

| Prediction result | Points |
|---|---|
| Exact scoreline | **3** |
| Correct goal difference, wins only (e.g. predicted 2-1, actual 3-2) | **2** |
| Correct outcome only — includes *draw predicted, match drawn, wrong scoreline* | **1** |
| Wrong outcome | **0** |

**Draw rule (explicit decision):** although every draw shares goal difference 0,
a draw prediction with the wrong scoreline scores **1 point**, not 2. The
2-point goal-difference tier applies to wins/losses only. Consequence: draws
skip the 2-point tier, making draw predictions bolder/lower-expected-value —
intentional.

### 2.2 Prediction locking

- **Per-match lock:** each prediction locks at that match's kickoff time.
- Predictions are editable freely until lock.
- Kickoff times are refreshed from the data provider (they move often); the
  lock always follows the latest known kickoff.
- Missed prediction = 0 points for that match. No whole-gameweek wipeouts.
- Other players' predictions for a match become visible only after that match
  locks (prevents copying).

### 2.3 Edge cases (to handle in the scoring engine)

- **Postponed / abandoned match:** removed from the gameweek, predictions
  voided (0-0-neutral: match simply doesn't count for anyone).
- **Result corrections:** provider may amend a result; scoring must be
  re-runnable idempotently for a match.
- Extra time / penalties (cup games): score at 90 minutes counts. (Confirm
  once cup fixtures actually appear in a gameweek.)

## 3. Gameweeks

- A **gameweek (GW)** is a curated set of fixtures with a number and a window.
- **Gameweeks are identified by number only** — GW1, GW2, GW3 … There is no
  weekend/midweek label anywhere in the product. *(Revised 2026-07: the
  original design split gameweeks into weekend and midweek cadences. In
  practice a gameweek is simply "the next batch of curated fixtures", which
  may fall on any days — labelling one "Weekend" is wrong as often as it is
  right, and players think in numbers. The `type` column survives in the
  schema as an unused legacy hint, defaulted and never displayed.)*
- Gameweeks may run at any cadence and vary in size; fairness holds within a
  gameweek because everyone predicts the same fixtures.
- Consequence: the parked "leagues can skip midweek gameweeks" setting is
  dropped. If per-league gameweek opt-outs are ever wanted, they should be
  expressed over fixture dates or explicit gameweek selection, not a type.
- The season is the European club season (Aug–May).

## 4. Fixture sources

Every league consumes fixtures from a **fixture source**:

1. **Elite League selection (default):** hand-curated by the game admin — a
   mix of big games across Europe each gameweek. This is the flagship feed.
2. **Single-league mode:** auto-follow one competition's matchdays (Premier
   League, La Liga, Serie A, Bundesliga, Ligue 1, Champions League).
3. **Random mode:** N fixtures drawn at random from the covered competitions
   for the GW window. (Exact draw rules TBD — see open questions.)
4. **League-admin curated (private leagues only):** the league admin may
   hand-pick fixtures for a gameweek, **with auto-fallback**: if the admin
   hasn't confirmed a selection by the cutoff (48h before the GW's first
   kickoff), the league silently falls back to its configured preset source.
   No dead weeks, no discipline burden.

Decision resolved: the game admin (you) curates the Elite League centrally;
private leagues get autonomy via option 4 with the fallback safety net.

## 5. Leagues

### 5.1 Public leagues (automatic, points-based only)

Users join three public leagues automatically at enrollment:

| League | Membership |
|---|---|
| **Global** | everyone |
| **Country** | users with the same country |
| **Club** | users with the same favourite club |

- Country and favourite club are collected at first enrollment and **editable
  anytime**.
- **Dynamic-view model:** public leagues are *live filters over user
  attributes*, not materialized memberships. Changing club/country instantly
  moves the user — with their full season total — to the new table. No
  membership migration logic; league-shopping is accepted as harmless in a
  fun game.
- All public leagues play the **Elite League fixture source**.

### 5.2 Private leagues

- Created by any user, who becomes league admin; members join via invite
  link/code.
- Two formats:
  - **Points league:** classic table ranked by prediction points.
  - **Head-to-head league:** FPL-style (below).
- Settings at creation: name, format, fixture source (Elite / single league /
  random / admin-curated-with-fallback), gameweek participation (weekend-only
  or all), start gameweek.

### 5.3 Late joiners

- **Points leagues:** each member's league score counts **from the gameweek
  they joined**. Founders aren't leapfrogged retroactively; newcomers start a
  fresh race. (Global prediction totals still exist app-wide for public
  leagues and profiles.)
- **H2H leagues:** **closed to new members** once the league's first round is
  played. Keeps the round-robin symmetric.

### 5.4 Head-to-head format (FPL-style)

- Each gameweek the league plays is one H2H round.
- Round-robin schedule generated at league start, repeating until season end.
- Your GW prediction score vs your opponent's: win = 3, draw = 1, loss = 0.
- Odd member count → one member faces the **league average** that round.
- Final table decides the winner; tiebreak = total prediction points. No
  playoffs in v1.

### 5.5 Later (explicitly out of v1)

- **Overall Cup** (FPL-style knockout across all players).
- H2H playoffs, achievements/badges, social feed.

## 6. Match data

- **Provider: football-data.org free tier** — covers PL, La Liga, Serie A,
  Bundesliga, Ligue 1, Champions League; ~10 req/min; results land within
  minutes of full-time (fine, we score after FT).
- **Adapter layer:** a `FixtureProvider` port with a football-data.org
  implementation. Swapping to API-Football later (live scores, more leagues)
  is a new adapter, not a rewrite.
- Sync jobs:
  - **Fixture/kickoff sync:** daily full sync + hourly refresh on matchdays
    (kickoff times move; locks depend on them).
  - **Result polling:** during match windows, poll ~every 5 min; on FT,
    trigger scoring for that match.

## 7. Platform & UX

- **Mobile-first PWA:** one React codebase, responsive, installable, **web
  push** for deadline reminders ("3 matches kick off in 3h — 2 predictions
  missing"). Email as notification fallback. Native apps deferred.
- **Auth:** Google OAuth + email/password. Invite-link → signup → predicting
  in under a minute is the north-star onboarding flow. Apple sign-in later.
- **Onboarding:** sign up → pick country → pick favourite club → land on the
  current gameweek's prediction screen, already enrolled in 3 public leagues.

### Key screens (v1)

1. **Predict** (home): current GW fixtures, inline score steppers, lock
   countdowns, saved-state feedback. Zero-friction — this is the screen.
2. **Gameweek results:** per-match breakdown of your points, reveal of
   rivals' predictions post-lock.
3. **Leagues:** list of my leagues → table view (points) or fixtures/table
   view (H2H).
4. **League create/join:** create wizard + invite link handling.
5. **Profile/settings:** country, club, notification preferences.
6. **Admin (you):** Elite GW curation, fixture pool browser, publish/cutoff
   controls; league-admin variant for private curated leagues.

## 8. Architecture

- **Backend:** Java, **Spring Boot 4.1** (Spring Framework 7, virtual
  threads). REST API. Spring Security (OAuth2 login + JWT/session for the
  SPA). Spring `@Scheduled` for sync/scoring/notification jobs. JPA +
  **PostgreSQL**. Flyway migrations.
- **Frontend:** React (Vite), TypeScript, PWA (service worker, web push).
- **One deployable** (API + jobs in the same Spring app) until scale demands
  otherwise. No message broker in v1; scheduled polling + DB is enough.

### 8.1 Domain model sketch

- `User` (id, email, auth identities, displayName, **country**, **favouriteClub**, notification prefs)
- `Competition` (provider-mapped: PL, PD, SA, BL1, FL1, CL)
- `Team`, `Match` (competition, home/away, kickoffUtc, status, score, providerRef)
- `Gameweek` (season, index, type: WEEKEND|MIDWEEK, window, status: DRAFT|PUBLISHED|SCORED)
- `GameweekFixture` (gameweek ↔ match; for Elite: curated set. Per-league curated sets reference their league)
- `Prediction` (user, match, gameweek-context, homeGoals, awayGoals, lockedAt, points)
- `League` (type: PUBLIC_GLOBAL|PUBLIC_COUNTRY|PUBLIC_CLUB — virtual — or PRIVATE_POINTS|PRIVATE_H2H; fixtureSource, gwParticipation, adminUser, inviteCode)
- `LeagueMembership` (private leagues only; joinedAtGameweek)
- `H2HFixture` / `H2HStanding` (per H2H league per round)
- Public league tables: queries over `User` attributes + prediction point sums — no membership rows.

### 8.2 Scoring engine

- Pure function: `(prediction, finalScore) → 0|1|2|3` implementing §2.1
  (including the explicit draw rule). Property-test this exhaustively.
- Triggered per match on FT; idempotent re-run on result correction;
  cascades: match points → GW totals → points-league tables (from-join-GW
  windows) → H2H round resolution when all of a round's matches are scored.

## 9. Decision log

| # | Question | Decision |
|---|---|---|
| 1 | Draw predicted, wrong scoreline | 1 point (outcome tier; no 2-pt tier for draws) |
| 2 | Private-league fixtures | Preset source + admin override with auto-fallback at cutoff |
| 3 | Prediction deadlines | Per-match lock at kickoff |
| 4 | Gameweek structure | ~~Weekend GWs + separate midweek GWs; per-league opt-out of midweek~~ → **revised 2026-07**: gameweeks are numbered only, any cadence, no weekend/midweek label (see §3) |
| 5 | H2H format | Pure FPL round-robin, 3/1/0, average for odd counts, tiebreak on total points |
| 6 | Data source | football-data.org free tier behind a provider adapter |
| 7 | Platform | Mobile-first PWA with web push, email fallback |
| 8 | Club/country change mid-season | Public leagues are dynamic views; full points move instantly |
| 9 | Auth | Google OAuth + email/password |
| 10 | Late joiners | Points leagues count from join-GW; H2H closes after round 1 |
| 11 | Stack | React + Spring Boot 4.1 + PostgreSQL |

## 10. Open questions (parked, non-blocking)

1. **Random mode definition:** how many fixtures, weighted toward big games
   or uniform, re-rollable by the admin?
2. **Points-league tiebreakers:** suggested — most exact scores, then most
   correct outcomes, then earliest to reach the total.
3. **Bonus ideas for later:** captain/double-up fixture? streak bonuses?
   (Keep v1 pure.)
4. **Cup design** (knockout seeding, entry criteria) — post-v1.
5. **Extra-time rule** confirmation when cup/CL knockout fixtures enter GWs.
6. **League size limits** for private leagues (H2H round-robin needs a cap,
   e.g. 20).
7. **Moderation basics:** display-name policy, league-name policy, kick/ban
   by league admin.
