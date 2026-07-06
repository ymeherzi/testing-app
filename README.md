# Predictor — football score prediction game

Predict scorelines for curated big matches across Europe, earn points on a
tiered scale, and climb season-long league tables. The full product and
architecture design lives in [docs/DESIGN.md](docs/DESIGN.md); this repo
currently implements **milestone 1: the core game loop** — signup → predict →
results → scoring → global table.

## Scoring

| Prediction result | Points |
|---|---|
| Exact scoreline | 3 |
| Correct goal difference (wins only) | 2 |
| Correct outcome only — incl. a drawn draw with the wrong scoreline | 1 |
| Wrong outcome | 0 |

Predictions lock per match at kickoff, enforced server-side.

## Stack

- `backend/` — Java 21, Spring Boot 4.1, PostgreSQL + Flyway, stateless JWT
  auth (Spring Security 7 resource-server), football-data.org behind a
  `FixtureProvider` port with an offline seed adapter.
- `frontend/` — React 19 + Vite + TypeScript, Tailwind v4, TanStack Query,
  installable PWA. Mobile-first.

## Run locally (no API key needed)

```bash
# 1. Postgres
docker compose up -d

# 2. Backend (dev profile: seed fixtures, demo gameweek, admin account)
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev

# 3. Frontend (proxies /api to :8080)
cd frontend && npm install && npm run dev
```

Open http://localhost:5173.

- Admin account: `admin@dev.local` / `admin123!`
- The dev profile seeds deterministic fixtures relative to *now* (one
  finished, one live, several upcoming) and publishes a demo gameweek, so
  the app is playable immediately.

### Real data

Set `FOOTBALL_DATA_API_KEY` (free at football-data.org) and
`APP_FIXTURES_PROVIDER=footballdata`, and enable jobs with
`APP_JOBS_ENABLED=true` — see `.env.example`. Calls are rate-limited to 8/min
(free tier allows 10).

## Try the whole loop

1. Sign up a user (pick a country + favourite club) at `/signup`.
2. Predict scores on the open fixtures — saves automatically per match.
3. Try a locked (started/finished) match: the API answers 409 and the UI
   shows it read-only.
4. Log in as the admin → Admin tab → set a result on a predicted match
   (dev-profile simulation). Try a draw to see the 1-point draw rule.
5. Back as the user: the match card shows your points; the Table tab shows
   the global ranking.

## Tests

```bash
cd backend && ./mvnw verify     # unit + property + Testcontainers integration tests
cd frontend && npm run lint && npm test && npm run build
```

CI runs both suites on every push (`.github/workflows/ci.yml`).

## API sketch

| Method | Path | Auth |
|---|---|---|
| POST | `/api/auth/signup`, `/api/auth/login` | public |
| GET | `/api/teams` | public |
| GET/PUT | `/api/me` | user |
| GET | `/api/gameweeks/current`, `/api/gameweeks/{id}` | user |
| PUT | `/api/gameweeks/{gw}/fixtures/{fx}/prediction` | user (409 when locked) |
| GET | `/api/leagues/global/table?page&size` | user |
| GET/POST/PUT | `/api/admin/gameweeks…`, `/api/admin/matches`, `/api/admin/sync/fixtures` | admin |
| POST | `/api/dev/matches/{id}/result`, `/api/dev/matches/{id}/kickoff` | admin, dev profile only |

## Deploy to Railway (phone-ready PWA)

The repo ships a production `Dockerfile` (React build baked into the Spring
Boot jar — one service, no CORS) and a `railway.json`. Steps:

1. **railway.com → New Project → Deploy PostgreSQL.**
2. **+ New → GitHub Repo** → pick this repo and the branch to deploy.
   Railway detects the Dockerfile automatically.
3. On the app service → **Variables**, add:

   | Variable | Value |
   |---|---|
   | `DB_URL` | `jdbc:postgresql://${{Postgres.PGHOST}}:${{Postgres.PGPORT}}/${{Postgres.PGDATABASE}}` |
   | `DB_USER` | `${{Postgres.PGUSER}}` |
   | `DB_PASSWORD` | `${{Postgres.PGPASSWORD}}` |
   | `JWT_SECRET` | output of `openssl rand -base64 48` |
   | `APP_ADMIN_EMAIL` / `APP_ADMIN_PASSWORD` | your admin login (created at first boot) |
   | `APP_FIXTURES_PROVIDER` | `seed` for demo mode, `footballdata` for real data |
   | `FOOTBALL_DATA_API_KEY` | only with `footballdata` |
   | `APP_JOBS_ENABLED` | `true` with `footballdata`, else `false` |

4. **Settings → Networking → Generate Domain.**
5. Open the URL on your phone; sign in as the admin, hit **Admin → Sync
   fixtures**, create + publish a gameweek, and share the URL with friends.
   In the browser menu choose **Add to Home Screen** to install the PWA.

Demo-mode note: the seed provider regenerates fixtures relative to *now* on
every restart (results reset). With `footballdata` the scheduled jobs keep
fixtures and results real and results never regress.

## Roadmap (from the design doc)

Country/club public leagues → private leagues (points + FPL-style H2H) →
Google OAuth → web push deadline reminders → random fixture mode → cup.
