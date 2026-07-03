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

## Roadmap (from the design doc)

Country/club public leagues → private leagues (points + FPL-style H2H) →
Google OAuth → web push deadline reminders → random fixture mode → cup.
