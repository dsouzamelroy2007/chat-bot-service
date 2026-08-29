# Deploying the backend (Render + Neon + Upstash)

The target stack (decided ahead of this phase, see `docs/PLAN.md` Phase 6 target platform notes):
**Render** for the app itself (builds straight from the existing `Dockerfile`), **Neon** for
Postgres, and **Upstash** for Redis. All three have a permanent free tier, matching this project's
zero-cost-by-default posture — the same reasoning already applied to every free-tier LLM provider
and tool in `.env.example`.

These steps were actually executed against a real Render account, real Neon database, and real
Upstash instance (2026-08-29) -- unlike `docs/DEPLOY_WIDGET.md`'s Vercel steps, which remain
docs-only. The first real deploy attempt failed with `COPY target/*.jar app.jar` erroring
`lstat /target: no such file or directory`: the `Dockerfile` was a single stage that assumed a
pre-built jar already sat in `target/` (true locally via `start.sh`'s `mvn package` step, never true
when a host builds straight from the git repo with no separate build step of its own, which is
exactly what Render does). Fixed by making it a proper multi-stage build -- a `maven:3.9-eclipse-
temurin-21` stage compiles the jar itself, then the final `eclipse-temurin:21-jre-alpine` stage
copies it out, same as before. Verified locally by building the image directly from a checkout with
no local `target/` present (a `.dockerignore` now excludes it from the build context regardless),
confirming the exact failure Render hit is reproduced and fixed, not just reasoned about. Still
secret-free either way -- no `ARG`/`ENV` baked in at any stage.

## 1. Provision Neon (Postgres)

Create a free Neon project and database. Neon gives you a connection string like:

```
postgresql://<user>:<password>@<host>.neon.tech/<database>?sslmode=require
```

Split that into the individual `DB_*` env vars the app already reads (`application.yml`):

| Env var | Value |
|---|---|
| `DB_HOST` | the `<host>.neon.tech` part |
| `DB_PORT` | `5432` |
| `DB_NAME` | `<database>` |
| `DB_USER` | `<user>` |
| `DB_PASSWORD` | `<password>` |
| `DB_SSLMODE` | `require` |

`DB_SSLMODE` is new in Phase 6 (default `prefer`, which is a no-op against the local/docker-compose
Postgres and would in principle already negotiate SSL against Neon on its own) — set it to `require`
explicitly anyway, matching Neon's own documented recommendation, rather than relying on `prefer`'s
implicit fallback behavior. Flyway migrates Neon exactly as it does the local Postgres container —
nothing extra to run by hand, it applies on boot.

## 2. Provision Upstash (Redis)

Create a free Upstash Redis database. Its console gives you the endpoint, port, and password
separately (as well as a combined `rediss://` URL, which this app doesn't consume directly — see
below). Map them to:

| Env var | Value |
|---|---|
| `REDIS_HOST` | the endpoint hostname Upstash shows |
| `REDIS_PORT` | the port Upstash shows |
| `REDIS_PASSWORD` | the password/token Upstash shows |
| `REDIS_SSL_ENABLED` | `true` |

`REDIS_PASSWORD`/`REDIS_SSL_ENABLED` are new in Phase 6, both blank/`false` by default so the
existing local/docker-compose Redis (no auth, no TLS) needs neither. Upstash requires both. (The app
reads these as discrete `spring.data.redis.*` properties rather than a single connection URL,
deliberately — binding `spring.data.redis.url` to a blank string when unset, rather than leaving it
absent, turned out to change Spring Data Redis's connection-building path entirely, confirmed by
decompiling the actual resolved classes rather than assumed; the host/port/password/ssl-enabled
shape avoids that risk.)

## 3. Deploy the app to Render

**Manual (dashboard) — the verified path:**

1. **New +** → **Web Service**, connect this repo.
2. **Runtime**: Docker (Render detects the `Dockerfile` automatically).
3. **Health Check Path**: `/bot/actuator/health`.
4. **Environment** tab: add every env var from steps 1–2 above, plus at least one free-tier LLM
   provider key (`GEMINI_API_KEY`, `GROQ_API_KEY`, etc. — see `.env.example`; without one, every
   reply falls back to the canned "no reply" response, same as running locally with no keys).
   Anything left unset here behaves exactly as it does locally with no `.env` entry — every one of
   these is optional/self-disabling by the same convention used throughout this project.
5. Deploy. Render builds the image from `Dockerfile` and starts the container — no `PORT` env var
   needs setting by hand, Render injects one automatically and the app now listens on it
   (`server.port: ${PORT:8080}`, Phase 6).

**Blueprint (optional, unverified):** [`render.yaml`](../render.yaml) at the repo root describes the
same service for Render's **New +** → **Blueprint** flow. It leaves every secret (`sync: false`) for
you to fill in via the dashboard after it applies — nothing sensitive is in the committed file. This
wasn't tested against a live account; if it doesn't parse or apply cleanly, fall back to the manual
steps above.

## 4. Set the production-facing config

- **CORS**: if the widget is deployed separately (see `docs/DEPLOY_WIDGET.md`), set
  `CHATBOT_CORS_ALLOWED_ORIGINS` on Render to the widget's real origin — the default value only
  covers local dev servers.
- **API key** (Phase 5): set `CHATBOT_API_KEY` before treating this as genuinely public. Unset means
  `/chat/**` has no auth check at all (a startup log warns when this is the case) — fine while
  you're the only caller, not once the URL is public. If the widget is the client, also set
  `window.CHAT_API_KEY` in its page (see `docs/DEPLOY_WIDGET.md` and the `Security` section of the
  main README for what this does and doesn't protect against).

## 5. Verify

```bash
curl https://your-backend.onrender.com/bot/actuator/health
curl -X POST https://your-backend.onrender.com/bot/chat/reply \
  -H "Content-Type: application/json" \
  -H "X-API-Key: <value, if CHATBOT_API_KEY is set>" \
  -d '{"botId": "support-bot", "userId": "user-123", "message": "Hello, what are your hours?"}'
```

The first confirms the app, Neon, and Upstash are all reachable (health rolls up datasource and
Redis connectivity); the second confirms an end-to-end reply through whichever provider is next in
priority.
