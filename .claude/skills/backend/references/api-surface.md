# Bunny Code API surface — v2

Source of truth for clients. Generated from the controllers under `src/main/java/com/bunnycode/*/api/`;
regenerate by reading them if anything here looks stale. The executable version of this contract is
the Cucumber suite in `src/test/resources/features/`.

- REST base path: `/api/v2`. JSON in and out; timestamps are ISO-8601 UTC unless stated otherwise.
- Auth: `Authorization: Bearer <jwt>` **or** the httpOnly `bunny_session` cookie (same JWT). Header wins.
  Cookie-authenticated writes must echo the `XSRF-TOKEN` cookie in an `X-XSRF-TOKEN` header.
- Tokens expire (12 h by default); `expiresAt` is returned at sign-in.
- Errors are RFC 9457 problem details: `{type, title, status, detail, code, errors?}`. Show `detail`
  to users; branch on `code`. Validation failures add `errors: [{field, message}]`.
- Paging: `?page` (zero-based, in **and** out) and `?size` (1–50, default 6) →
  `{content, page, size, totalElements, totalPages}`.
- Names (project, version, file, battle) match `^[a-zA-Z0-9_\-.]{1,20}$`. Passwords: 6–30 chars with a
  lower-case letter and a digit (sign-up only).

## Identity

| Method | Path | Auth | Body / query | Success |
|---|---|---|---|---|
| POST | `/auth/sign-up` | — | `{name, email, password}` | 201 `{accessToken, tokenType, expiresAt, user}` · 409 `user.email-taken` / `user.name-taken` |
| POST | `/auth/sign-in` | — | `{email, password}` | 200 same shape · 401 `auth.invalid-credentials` |
| GET | `/users/me` | required | — | 200 `{id, name, email, pictureUrl, profile, level}` |
| GET | `/users/{userId}` | — | — | 200 same without `email` · 404 `user.not-found` |

## Projects

`ProjectSummary = {id, name, description, visibility: PUBLIC|PRIVATE, watchCount, starCount, createdAt, latestVersionName, owner: {id, name, pictureUrl}}`
— `latestVersionName` is the highest-numbered version's name (null without versions), read in the same query.

| Method | Path | Auth | Body / query | Success |
|---|---|---|---|---|
| GET | `/projects` | — | `keyword?` (name, description or owner name), `sort=RECENT\|POPULAR\|STARRED`, paging | 200 page of `ProjectSummary` (public only). Home page "most starred" = `?sort=STARRED&size=3` |
| GET | `/users/{userId}/projects` | optional | `keyword?`, paging | 200 page; private projects only when the caller is that user |
| GET | `/projects/{name}` | optional | — | 200 `ProjectSummary + {starredByViewer, versions: [Version]}` · 404 also for others' private projects |
| POST | `/projects` | required | `{name, description (0–50), visibility, versionName, fileName}` | 201 `{id, name}` + `Location` · 409 `project.name-taken` |
| POST | `/projects/{name}/versions` | owner | `{versionName, fileName}` | 201 `Version` (next number, copy of the latest version's file) · 403 `project.not-owner` · 409 `version.name-taken` |
| POST | `/projects/{name}/views` | optional | — | 204 (counted once per viewer per hour; owners never count) |
| PUT / DELETE | `/projects/{name}/star` | required | — | 204 (idempotent; one star per user) |

`Version = {id, name, number, file: {id, name, url, revision} | null, recording: {id, baseFileUrl, startedAt, endedAt} | null}`.
A version with a `recording` is replay-only.

Names: project, file and battle names are 1–20 of `[A-Za-z0-9_.-]`; version names are 1–30
characters with no control characters and no leading or trailing space.

## Statistics

| Method | Path | Auth | Success |
|---|---|---|---|
| GET | `/stats/projects` | — | 200 `{projects, recordings}` — public projects, and recorded versions of them |
| GET | `/stats/battles` | — | 200 `{battles}` — battles waiting, running or finished |

## Recording (replay)

| Method | Path | Auth | Body | Success |
|---|---|---|---|---|
| POST | `/projects/{name}/versions/{number}/recording` | owner | `{events: [{action, line, index, code, timestamp}], code}` | 201 `{recording, file}` · 409 `recording.exists` · 400 `recording.empty` / `recording.too-long` |
| GET | `/projects/{name}/versions/{number}/recording` | optional (visibility) | — | 200 `{id, baseFileUrl, startedAt, endedAt, format: V2\|LEGACY, events: [...]}` |

- `timestamp` is **epoch milliseconds**; `action` matches `^[A-Za-z]{1,20}$`; `code` is the final file content.
- `baseFileUrl` is the content the replay starts from (the version's file before the session).
- `format: LEGACY` (recorded by v1): a lone `"` arrives as `""`, `""` as `""""`, and line breaks as `\r\n`.
  `V2` events are returned exactly as recorded.

## Code execution

| Method | Path | Auth | Body | Success |
|---|---|---|---|---|
| POST | `/executions` | required | `{code}` | 200 `{status: SUCCEEDED\|FAILED\|TIMED_OUT\|OUT_OF_MEMORY, output}` · 429 `execution.rate-limited` · 503 `execution.busy` / `execution.unavailable` |

Program failures are **200** results: render `output` verbatim in the terminal pane.

## Battles

`BattleSummary = {id, name, status: WAITING|RUNNING|FINISHED|TERMINATED, watchCount, questionName, questionLevel: EASY|MEDIUM|HARD, players: [UserSummary, UserSummary], winnerId}`

| Method | Path | Auth | Query | Success |
|---|---|---|---|---|
| GET | `/battles` | — | `status=WAITING\|RUNNING\|FINISHED\|TERMINATED` (repeatable; default WAITING, RUNNING, FINISHED), `level=EASY\|MEDIUM\|HARD?`, `keyword?`, `searchBy=NAME\|QUESTION\|PLAYER`, paging | 200 page of `BattleSummary` (by watch count, then newest) |
| GET | `/battles/name-availability` | — | `name` | 200 `{name, available}` |
| GET | `/battles/{id}` | — | — | 200 `{…summary, question: {name, level, descriptionUrl, starterCodeUrl}, winner, winnerCodeUrl}` (review page) |

## Realtime — STOMP over WebSocket

Endpoint `/api/v2/ws` (native WebSocket, no SockJS). Send `Authorization: Bearer <jwt>` in the CONNECT
frame, or rely on the session cookie set before the handshake. Unauthenticated CONNECTs are refused.
Every failure arrives on **`/user/queue/errors`** as `{code, message}` — subscribe to it first.

### Workspace edit lock

| Frame | Destination | Payload | Reply on `/user/queue/edit-lock` |
|---|---|---|---|
| SEND | `/app/versions/{versionId}/edit-lock` | `{takeOver: false}` | `{versionId, state, message}` |
| SEND | `/app/versions/{versionId}/edit-lock` | `{takeOver: true}` | same; the previous window receives `REVOKED` |
| SEND | `/app/versions/{versionId}/edit-lock/release` | — | `RELEASED` |

`state`: `EDITING` · `LOCKED` (your other window edits; offer take-over) · `READ_ONLY` (not yours) ·
`RECORDED` (replay-only) · `REVOKED` (another window took over) · `RELEASED`. Disconnecting releases
the session's locks. Locks are per window, not per user.

### Battle Valley

| Frame | Destination | Payload / notes |
|---|---|---|
| SUBSCRIBE | `/topic/battle-invitations` | `{invitationId, battleName, level, inviterId, inviterName, expiresAt}` |
| SUBSCRIBE | `/user/queue/battles` | `{battleId}` — your battle is ready (both players) |
| SEND | `/app/battle-invitations` | `{battleName, level}` · errors `battle.name-taken`, `battle.name-pending`, `battle.invite-throttled` (one per 10 s) |
| SEND | `/app/battle-invitations/{invitationId}/accept` | errors `battle.invitation-expired`, `battle.self-invite` |
| SUBSCRIBE | `/app/battles/{battleId}` | one-off snapshot `{id, name, status, question, players: [{user, ready, code, chancesLeft}], viewerRole: PLAYER\|SPECTATOR, winnerId}`; spectators add a view |
| SUBSCRIBE | `/topic/battles/{battleId}` | live events, below |
| SEND | `/app/battles/{battleId}/ready` | players only |
| SEND | `/app/battles/{battleId}/code` | `{code}` — players only, broadcast to the room |
| SEND | `/app/battles/{battleId}/submissions` | `{code}` — uses one of 3 chances; judged asynchronously |
| SEND | `/app/battles/{battleId}/leave` | leaving (or disconnecting from) a RUNNING battle forfeits it |

Room events carry a `type`:

| `type` | Fields |
|---|---|
| `PLAYER_READY` | `userId` |
| `STARTED` | `startedAt` (exactly once, when both are ready) |
| `CODE_CHANGED` | `userId, code` |
| `SUBMISSION_JUDGED` | `userId, verdict (ACCEPTED\|WRONG_ANSWER\|RUNTIME_ERROR\|TIME_LIMIT_EXCEEDED\|MEMORY_LIMIT_EXCEEDED), testCases, output, chancesLeft` |
| `FINISHED` | `winnerId, winnerName, reason` |
| `TERMINATED` | `reason` |

`testCases` lists results up to the first failure: `{number, hidden, passed, arguments?, expected?, actual?, error?}`.
Cases after the first 3 are hidden: only `number`, `hidden` and `passed` are sent.
