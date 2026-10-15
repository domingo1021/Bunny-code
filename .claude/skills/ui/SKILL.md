---
name: ui
description: Design and review the Bunny Code interface — the workspace editor and replay player, Battle Valley, project browsing, auth forms, and their Bootstrap/CSS implementation. Use this skill for layout, visual hierarchy, component states, empty/loading/error states, responsiveness, accessibility, copy and microinteractions, i.e. whenever the request is "make this look better", "design the X screen", "the editor feels cramped", "add a loading state", or a review of how a screen reads — as opposed to wiring data, which belongs to the frontend skill.
---

# Bunny Code UI

Bunny Code is a coding site with two signature experiences: **Workspace** (edit, run and replay a coding process like a video) and **Battle Valley** (two people race on a question while spectators watch live). The UI's job is to make a timeline and a live contest legible — everything else on screen is supporting cast.

Stack: Vue.js components, Bootstrap for grid and base components, plain CSS for the rest, in the `Bunny-code-vue` repo. Prefer Bootstrap's existing utilities and components over new CSS; reach for custom styling only for the editor, the replay timeline and the battle layout, which have no Bootstrap equivalent.

## Design principles for this product

**The code is the content.** On workspace and battle screens the editor gets the space and nothing competes with it. Chrome (nav, metadata, buttons) should be quiet and compact; a toolbar that steals vertical space from the editor is the most common regression here.

**Always show who is in control.** Editing is an exclusive lock, and a viewer can be kicked out of edit mode when someone else takes it. Whether the current user is editing, read-only, or watching must be visible at a glance and never inferred from whether typing happens to work. Use a persistent badge or banner, not a transient toast.

**Replay is a player, so it should feel like one.** Play/pause, scrub, and speed (the product ships x1 and x2) belong in one familiar control cluster with keyboard support. Current position and total length should be readable without hovering.

**A battle needs a clock and a verdict.** Both sides' progress, the question, run output, expected-vs-actual test results, and the outcome need fixed places on screen that don't reflow as events arrive. Layout shift during a live match is disorienting; reserve the space up front.

## States every screen owes the user

Bunny Code has more unhappy paths than a typical CRUD app, and most of them come from the server, so they can't be designed away:

- **Loading** — skeletons for project lists and the editor shell; never a bare spinner over an empty page.
- **Empty** — no projects yet, no versions, no battles running: each needs a one-line explanation and the obvious next action.
- **Read-only** — the default for anyone who isn't the owner, and for an owner who lost the lock. Disable the controls visibly rather than letting input fail silently.
- **Kicked out** — the server can disconnect an editor when another session takes over. Explain what happened in plain language and offer "take editing back" instead of showing a generic disconnect error.
- **Running code** — the sandbox allows up to 10 seconds, so the run button needs a busy state and the output pane needs a "still running" affordance.
- **Code failed** — three distinct outcomes with different meanings: the user's own error (stderr), a 10-second timeout, and out-of-memory. The backend writes user-facing text for each; present it as program output in a monospace pane, not as a UI error dialog, because it's a result the user is meant to read and learn from.
- **Disconnected / reconnecting** — websockets drop. Show it, keep the last state on screen, and don't silently diverge from the server.
- **Form validation** — mirror the server's rules inline (names: letters, digits, `_`, `-`, `.`, max 20; description 1–30; password 6–30 with a letter and a digit; uploads: JavaScript only, max 5 files, under 3 MB) so users don't learn them from a 400.

## Interaction details

- Workspace advertises keyboard shortcuts as a feature. Any new action should get one, shortcuts need a discoverable list, and they must not collide with the editor's own bindings or the browser's.
- Buttons that cost real work (run, create version, launch battle) get immediate feedback and protection against double-submit — these hit a sandbox or a transaction.
- Destructive or hard-to-undo actions (abandoning a battle, overwriting someone's editing session) confirm first and say what will happen to the other person.
- Live-updating regions (opponent's code, spectator counts, run output) should update in place; avoid animations that make a moving target of something being read.

## Accessibility and responsiveness

- Keep contrast legible in the code panes and the timeline, not just the marketing surfaces; test the editor theme's syntax colors rather than assuming the library's defaults pass.
- Everything reachable by mouse must be reachable by keyboard, with visible focus. The player controls and the battle actions are the usual offenders.
- Announce state changes that aren't where the user is looking (lock taken, battle started, run finished) via a live region, not colour alone.
- The editor and battle layouts need a deliberate small-screen story — usually one pane at a time with a switcher, rather than a squeezed side-by-side. Project browsing and auth should simply work at phone width on the Bootstrap grid.

## Copy

Plain, specific, second person. "Someone else is editing this version" beats "Lock acquisition failed". Don't surface internal vocabulary (`versionID`, `clientCategory`, `OOM`) — the one exception is raw program output, which should stay verbatim. Keep the existing friendly product voice ("Bunny is too busy to run code — try again in a moment") rather than switching to clinical error text.

## Before calling a UI change done

Walk the screen in each state above, at phone and desktop width, with a keyboard only. Most defects here aren't visual — they're a state nobody designed, which then shows up as a blank panel or a dead button in front of a real user.
