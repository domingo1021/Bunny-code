---
name: frontend
description: Work on the Bunny Code client — the Vue.js + Bootstrap app in the separate Bunny-code-vue repo, plus anything in public/ here. Covers calling the REST API, wiring Socket.IO events, JWT handling in the browser, the workspace replay player and the battle screens. Use this skill whenever the request is about client-side behaviour, "call this endpoint from the frontend", "the socket isn't connecting", "CORS error", "the replay stalls", "add a page/component", or integrating a new backend field into the UI — even if the frontend repo isn't open yet.
---

# Bunny Code frontend

> **API v2 is live on the backend `v2` branch.** It replaces `/api/1.0` + Socket.IO with `/api/v2` REST
> and **STOMP over WebSocket** at `/api/v2/ws`. `backend/references/api-surface.md` describes v2 and is the
> contract for the Next.js rewrite (docs/MIGRATION_PLAN.md, Phases 8–12). The Socket.IO and v1 notes
> below describe the current Vue client only and go away with it.

The client is a **separate repository**: [`domingo1021/Bunny-code-vue`](https://github.com/domingo1021/Bunny-code-vue) (Vue.js, Bootstrap, plain CSS). This repo (`Bunny-code`) is the API, and its `public/` folder holds only a static `index.html` socket smoke-test page and a `404.html` — that is not the product UI, so don't build features there.

So the first question for any frontend task is **where the code lives**:

- If the task is a client change, check for a local checkout (e.g. a sibling directory of this repo). If there isn't one, say so and ask whether to clone `Bunny-code-vue` rather than guessing at its file layout — this skill deliberately doesn't invent component paths it can't see.
- If the task is really about the contract the client consumes (shape of a response, which event fires when), you can answer it entirely from this repo. That's the part this skill carries.
- Nginx serves the built client from `/app` with `try_files $uri /index.html`, so the app must work as a SPA with history-mode fallback.

## The contract

`.claude/skills/backend/references/api-surface.md` is the generated list of every endpoint and socket event with auth, inputs and response shapes. Read it before writing a request or a listener — matching it exactly is cheaper than debugging a 400.

The parts that bite most often:

- Every path is prefixed `/api/${API_VERSION}` → `/api/1.0/...`. In production nginx proxies `/api` to port 3000, so the client can use a relative base URL.
- Success is `{ data }`, failure is `{ msg }`. Render `msg` directly — the backend writes those strings for end users (the sandbox OOM and timeout messages are meant to be shown verbatim), while the operator detail stays server-side.
- Auth is `Authorization: Bearer <jwt>`, with the token in `localStorage` under `jwt`. Note `server/services/auth.js` treats the literal string `"null"` as "no token", which is what `localStorage.getItem` stringifies to — so clear the key on sign-out instead of storing `null`.
- 401 means the token is missing or expired → send the user to sign-in. 403 means the token is valid but this isn't their resource → don't retry.
- List endpoints return `{ projects, page, allPage }`, 6 per page, with `paging` zero-based in the request and `page` one-based in the response. Don't feed `page` back in as `paging`.
- File URLs from `detail` and from `POST /record/file` already include the CloudFront host; concatenating another base URL double-prefixes them.
- Uploads are multipart `files`, at most 5, JavaScript only (`application/javascript`), under 3 MB. Validate in the browser too so the user gets feedback before the round trip.
- Names the backend accepts (project, version, file, battle) are `^[a-zA-Z0-9_\-.]{1,20}$`; passwords need a lowercase letter and a digit, 6–30 chars. Mirror these in form validation so errors surface before submit.

## Socket.IO

```js
const socket = io(serverOrigin, {
  path: '/api/socket/',
  auth: (cb) => cb({ token: `Bearer ${localStorage.getItem('jwt')}` }),
});
```

The non-default `path` and the `Bearer ` prefix are both required; the handshake fails without them. Use the `auth` *callback* form, not a static object, so reconnects pick up a refreshed token. Pin the client to the 4.x line that matches the server's `socket.io` 4.5.

Realities the UI has to handle, because they're server-driven:

- **Editing is a lock, not a mode.** `changeEdit` can forcibly disconnect whoever was editing that version. A client may be dropped mid-session through no action of its own, so treat `disconnect` as an expected state with a clear message and a read-only fallback, not an error toast.
- `statusChecked` returns `{ readOnly, authorization }` — drive the editor's writability from that response rather than from local assumptions about ownership.
- Incomplete payloads don't error. Omitting `projectID`/`versionID` quietly returns the read-only viewer response, which looks like a permissions bug from the UI side. Always send both.
- `Internal server error` is emitted as an *event name* (not an error object) whenever a handler throws. Listen for it explicitly or failures look like silence.
- Battle flow is event-driven end to end: `inviteBattle` → `userInvite` → `acceptBattle` → `userReady`/`setReady` → `battleStart` → `newCodes` broadcasts → `compile`/`compileDone` → `battleOver`/`winnerData`, with `battleFailed`, `battleNotFound`, `inviteFailed` and `battleTerminate` as the off-ramps. Every one of those needs a UI state.

## Replay

Coding-process events are written to InfluxDB via `POST /record` and read back via `POST /history/:projectID`; playback (play, pause, speed up, slow down) is a client-side concern driven by those timestamps. Speed changes should re-time the remaining queue rather than re-fetching, and the replay must be safe to start before the full series has loaded.

## CORS

The server allows exactly one origin, from the `WHITE_LIST` env var (`https://www.domingoos.store` in the template), for both Express and Socket.IO. A CORS failure in local development is almost always `WHITE_LIST` not matching the dev server's origin — fix the API's env, don't add proxy hacks to the client. The scheme, host and port must all match, and `www.` matters.

## When a change spans both repos

Land the API change first and update `backend/references/api-surface.md`, then write the client against it. If you can only edit one side in this session, state plainly what the other side still needs — a client shipped against an endpoint that doesn't exist yet is worse than an explicit TODO.
