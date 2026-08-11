# Ten Games — football score prediction game

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
(free tier allows 10), and a 429 is waited out and retried once: the allowance
belongs to the account, so our own counter cannot be the whole story.

A competition that fails is logged and skipped, and the rest are still synced
and kept — `SyncSummary.failed` names them. This is not defensive
housekeeping: one 429 used to abort the run, roll back everything already
fetched, and skip the cup import that followed, which is how the season's
opening finals went missing with no error anyone would notice.

Competitions the free tier omits come from ESPN's public API, and are
imported **before** the league sync for the same reason: the two super cups,
the six domestic cups of the big five, and the Belgian and Scottish leagues —
the last two are there for the Old Firm and the Topper, not for their full
calendars.

Squad lists are pulled once a day, not on every sync. A fixture already
carries its two clubs and their crests, so the lists only keep the club
catalogue complete; fetching them hourly for a dozen competitions would spend
the whole free-tier allowance on data we already have.

**The two sources spell clubs differently**, and not only by punctuation:
football-data returns registered names ("FC Internazionale Milano",
"Olympique de Marseille", "Sport Lisboa e Benfica"), ESPN returns the short
ones. `ClubNames` carries an alias table that makes them converge. Without it
fourteen of the app's biggest clubs matched no real name, so the Derby della
Madonnina, Le Classique and Ajax–Feyenoord were listed as rivalries that could
never be recognised — and the same club arrived twice in the catalogue when a
cup came from the other source. `RealClubNamesTest` checks the editorial data
against names copied verbatim from both APIs.

**ESPN requires a `User-Agent` it recognises.** Its edge answers 403 to the
JDK client's default agent and to browser-shaped ones, while letting ordinary
HTTP tools through; both callers log and continue, so the failure is silent.
`EspnClientConfig` is the one place that sets it, and both the cup importer
and the live-score poller share that client.

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
| GET | `/api/push/key` | public (the VAPID public key) |
| GET/POST/DELETE | `/api/push/subscriptions` | user |
| GET/POST/PUT | `/api/admin/gameweeks…`, `/api/admin/matches`, `/api/admin/sync/fixtures` | admin |
| POST | `/api/admin/notifications/launch` | admin (season-launch announcement) |
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

## Email (account business only)

Signup, new-device logins and password resets email a six-digit code.
Delivery goes through [resend.com](https://resend.com) behind an
`EmailSender` port; with no provider configured the app falls back to
`LoggingEmailSender` so the flow still works locally.

Nothing else is ever emailed. Round reminders are push notifications — see
below.

Two providers are supported; an SMTP relay wins when both are set, but on
Railway only Resend can actually be used (see below).

| Variable | Meaning |
|---|---|
| `MAIL_SMTP_HOST` / `_PORT` / `_USERNAME` / `_PASSWORD` | any SMTP relay; port defaults to 587. Local development or a non-Railway host only |
| `RESEND_API_KEY` | resend.com over HTTPS — the one that works on Railway |
| `MAIL_FROM` | sender the provider has verified — **required** with either |
| `MAIL_LOG_CODES` | testing only: prints codes to the log (see the checklist below) |

**You need a domain you own. There is no working shortcut around it** —
this was established the hard way, so don't spend the afternoon again:

- Resend verifies a whole *domain*. Until one is verified, its shared
  `onboarding@resend.dev` sender answers 403 for every recipient except the
  account owner.
- A Railway-provided `*.up.railway.app` address can never be that domain:
  Railway allows no DNS records on it, so it can never be verified.
- Relays that verify a single *sender address* instead (Brevo, Mailjet)
  will not accept a free-mail sender: `@gmail.com` and friends cannot be
  authenticated, and since the 2024 Gmail/Yahoo sender rules such mail is
  rewritten, rejected, or filtered as spam.
- SMTP is unavailable on Railway below the **Pro** plan — ports 25/465/587
  are blocked on Free, Trial and Hobby. `SmtpEmailSender` is therefore for
  local development (Mailpit, MailHog) or a non-Railway host; it stays
  inert while `MAIL_SMTP_HOST` is empty.

So: buy a domain (~$10-15/year), verify it at resend.com/domains, publish
the records it shows (MX + SPF `TXT` on the `send` subdomain, DKIM `TXT` on
`resend._domainkey`, optionally `_dmarc`; on Cloudflare keep them **DNS
only**), and point `MAIL_FROM` at an address on it.

`MAIL_FROM` has no default on purpose: a wrong sender fails per user at
send time, while a missing one fails loudly at startup.

A refused send is reported, not swallowed: the API answers 502 and the
code screen shows it, because an account whose code never arrives cannot
be verified. Signup rolls back with it, so the address stays free.

## Round notifications (web push)

Players are notified when a round opens, and once more when the first kickoff
is within 12 hours if their card is still incomplete. `NotificationJobs` looks
every 15 minutes (with `APP_JOBS_ENABLED=true`); it reads only the database,
so the frequency costs nothing. The season-launch announcement is sent by hand
from `POST /api/admin/notifications/launch`.

Having a push subscription **is** the consent — there is no separate
preference to keep in step with it. A 404 or 410 from the push service means
the device is gone, and the subscription is deleted rather than retried
forever.

Two properties are enforced rather than intended, and both have tests:

- **Never twice.** A unique index on `(user_id, gameweek_id, kind)` decides
  it, with a partial index covering announcements that belong to no round.
- **Never recorded unless delivered.** The row and the send share a
  transaction, so a push that reaches no device rolls the row back and is
  retried on the next pass.

| Variable | Meaning |
|---|---|
| `VAPID_PUBLIC_KEY` / `VAPID_PRIVATE_KEY` | P-256 key pair, base64url. Generate with `openssl ecparam -genkey -name prime256v1` and export the raw scalar and uncompressed point |
| `VAPID_SUBJECT` | `mailto:` or `https:` URL a push service can use to reach you |

Without the keys the app still starts and `LoggingPushSender` records what it
would have sent — local development needs no keys.

**Encryption is implemented against the JDK** (`WebPushCrypto`, RFC 8291 over
RFC 8188) rather than pulled from a library: the usual Java dependency brings
Netty, jose4j and BouncyCastle 1.70 — abandoned in 2021, with known advisories
— to make one HTTPS POST. It is checked against the worked example in RFC 8291
§5, so a mistake a round-trip test would cancel out still fails the build.

**On iPhone and iPad, push works only once the PWA is installed to the Home
Screen** (iOS 16.4+). In a Safari tab, subscribing simply fails, so the opt-in
screen detects it and shows the install instructions instead of a button that
cannot work. This is an Apple restriction, not something the code can route
around.

## Before going live — security checklist

Things that are deliberately relaxed while testing and **must be revisited
before real users sign up**:

- [ ] **`MAIL_LOG_CODES` must be unset.** With it on, six-digit login codes
      are printed to the application log; anyone who can read logs can take
      over an account. Off by default — it only exists so the verification
      flow is testable before a mail provider is configured. It stops being
      needed the moment `RESEND_API_KEY` and a verified-domain `MAIL_FROM`
      can reach any inbox.
- [ ] **Move the JWT out of `localStorage`.** Today the session token is
      readable by any script on the page (XSS). The intended fix is a
      refresh token in an HttpOnly cookie; the resource-server side needs no
      changes.
- [ ] **Rotate the bootstrap admin password** (`APP_ADMIN_PASSWORD`) and any
      credential that has been pasted into a chat or issue.
- [ ] **Add rate limiting** on `/api/auth/*` — code entry is capped at five
      attempts per code, but nothing yet limits how many codes an address can
      request. Once delivery reaches any inbox, `/api/auth/resend` can be
      aimed at strangers, so this grows from a cost concern to an abuse one.

Reviewed and considered acceptable:

- `DevBootstrap` logs the dev admin password and the demo league's invite
  code. It runs only under the `dev` profile against a local throwaway
  database, and the values are fixed and public in this README.

## Roadmap (from the design doc)

Country/club public leagues → private leagues (points + FPL-style H2H) →
Google OAuth → web push deadline reminders → random fixture mode → cup.
