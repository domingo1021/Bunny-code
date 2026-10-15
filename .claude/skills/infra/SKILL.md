---
name: infra
description: Operate and change Bunny Code's infrastructure — the API Dockerfile and docker-compose stack (api, MySQL, Redis, InfluxDB, nginx), the sandbox host image, nginx/TLS config, GitHub Actions (CI and deploy), environment variables, Flyway migrations, InfluxDB, Redis, and the S3/CloudFront setup. Use this skill whenever work touches Dockerfile, Docker/, .github/workflows/, src/main/resources/db/migration, application*.yml, nginx config or .env.example, or questions like "how does deploy work", "add an env var", "why is the container OOMing", "set up a new sandbox host", or "the deploy didn't restart the server".
---

# Bunny Code infrastructure (v2)

One API host behind nginx, plus separate **sandbox hosts** that run untrusted user code in
short-lived Docker containers. MySQL, Redis and InfluxDB back the API; S3 + CloudFront serve files.
`Archives/System_architecture.png` shows the topology (unchanged by v2 except the API runtime).

## What runs where

| Piece | Source | Notes |
|---|---|---|
| API (REST + STOMP) | `Dockerfile` → `bunny-api` | Spring Boot 4.1 on JRE 25, layered jar, non-root uid 10001, port **8080**, includes `openssh-client` for the sandbox transport |
| MySQL 8.4 | `Docker/docker-compose.yml` | schema owned by **Flyway** (`src/main/resources/db/migration`), applied at API start |
| Redis | `Docker/Cache/Dockerfile` | edit locks, battle live state, invitations, rate limits — required, not a cache |
| InfluxDB 2.7 | compose | coding-session events for replay |
| nginx | `Docker/ReverseProxy` | TLS, SPA/Next.js on `/`, `/api/` → `127.0.0.1:8080` with Upgrade headers (STOMP at `/api/v2/ws`) |
| Sandbox runner | `Docker/sandbox/Dockerfile` → tag **`node-tool`** on each sandbox host | Node 24; the API streams code over stdin |

Health: `/actuator/health/liveness` and `/actuator/health/readiness` (MySQL, Redis included).

## CI / deploy

- `.github/workflows/ci.yml` runs `./mvnw -B verify` on every PR and push: unit, slice, Testcontainers
  integration and Cucumber BDD tests, then the JaCoCo gate (85% lines). Runners need Docker.
- `.github/workflows/deploy.yml` is still the **v1** pm2/`git pull` deploy and does not work for v2
  (no Node app any more). Replacing it (image build + push + health-checked rollout) is pending a
  decision on the target (EC2 + compose vs. container service) — see `docs/MIGRATION_PLAN.md` P7.3.
  Do not merge `v2` into `main` before that, or the old workflow will fail on the next push.

## Database migrations

- `V1__baseline.sql` is the production schema as of 2022; existing databases are adopted with
  `spring.flyway.baseline-on-migrate=true` (V1 is marked applied, not run). Never edit applied files.
- `V2` reshapes v1 tables (password column width, drops `version.editing`, battle `status`, JSON test
  cases, `project_star`); `V3` seeds the battle questions. **Dry-run on a copy of production first**:
  MySQL DDL is not transactional, so a half-applied migration needs manual repair.
- Connections run in UTC (`connectionTimeZone=UTC`) and Hibernate writes UTC.

## Environment variables

`.env.example` is the single contract (names match the `${…}` placeholders in `application.yml`).
Configuration is validated at startup — a missing or short `JWT_SECRET_KEY` (< 32 chars), a bad
storage URL or an SSH sandbox without `SSH_IDENTITY_FILE` stops the API instead of misbehaving.
Groups: server (`SERVER_PORT`, `ALLOWED_ORIGINS` — a comma-separated list), MySQL (`DB_*`), Redis
(`CACHE_*`), InfluxDB (`INFLUX_*`), JWT (`JWT_SECRET_KEY`), storage (`AWS_*`, `S3_*`,
`DEFAULT_TEMPLATE_FILE`; `S3_ENDPOINT`/`S3_ACCESS_KEY` only for non-AWS endpoints), sandbox
(`SANDBOX_MODE=ssh|local`, `SANDBOX_IMAGE`, `SSH_IDENTITY_FILE`, `CPU_THRESHOLD`, `MEM_THRESHOLD`).
v1's `MEMORY_THRESHOLD`/`MEM_THRESHOLD` mismatch is gone.

## Sandbox hosts

The security boundary. User code never runs on the API host in production (`SANDBOX_MODE=local` is
for development and tests only).

- Candidate hosts are rows in the `host` table; the API probes each host's node-exporter
  (`:9100/metrics`) for CPU and memory, and uses the least-loaded host under the thresholds. If
  none qualifies, users see "Bunny is too busy…" (HTTP 503).
- The API runs `ssh -o BatchMode=yes ubuntu@host -- docker run --interactive --network none
  --read-only --cap-drop ALL --security-opt no-new-privileges --pids-limit 64 --cpus 0.2 --memory 20m
  --memory-swap 20m --entrypoint node node-tool -`, pipes the program to stdin, kills it after 10 s,
  checks `OOMKilled` and always `docker rm --force`s it. No files are copied (v1 used `scp`).
- A new host needs: Docker, the `node-tool` image, node-exporter on 9100 reachable from the API, the
  API's SSH key authorised for `ubuntu`, and a row in `host`.
- Never relax the limits, the timeout or the isolation flags to make user code succeed.

## nginx and TLS

`Docker/ReverseProxy/sites-enabled/bunny_example.conf` is the template (real configs and keys are not
committed). Keep the `Upgrade`/`Connection: upgrade` headers and the long `proxy_read_timeout` on
`/api/` or STOMP connections drop. With the Next.js frontend, `/` will proxy to the Next server
instead of serving `/app`; the session cookie works because everything shares one origin.

## Changing infrastructure

State the blast radius first and prefer the reversible step. Anything touching deploy workflows,
nginx, certificates, sandbox limits or a shared database is production-affecting: confirm with the
user, and never point the test suite at a non-disposable database (tests use Testcontainers only).
