#!/usr/bin/env node
/**
 * Seeds a locally running Bunny Code backend with demo data, through the
 * public API only — so it exercises the same paths the frontend does.
 *
 *   node Docker/local/seed.mjs [apiBaseUrl]     (default http://localhost:8080/api/v2)
 *
 * Idempotent: accounts that exist are signed into, projects that exist are
 * left alone, and versions that are already recorded are skipped. Run it as
 * often as you like.
 *
 * Accounts (password `test1234`, the same as the frontend's demo mode):
 *   domingoos@gmail.com — owns `hello-bunny` (recorded) and `scratchpad` (private)
 *   guest@gmail.com     — owns `fizz-buzz` (v1 recorded, v2 open for editing)
 */

const API = (process.argv[2] ?? process.env.BUNNY_API_URL ?? "http://localhost:8080/api/v2").replace(/\/$/, "");
const PASSWORD = "test1234";

const ACCOUNTS = [
  { name: "domingoos", email: "domingoos@gmail.com" },
  { name: "guest", email: "guest@gmail.com" },
];

const HELLO = `
function fibonacci(n) {
  let [a, b] = [0, 1];
  for (let i = 0; i < n; i++) [a, b] = [b, a + b];
  return a;
}

console.log(fibonacci(10));
`;

const FIZZ_BUZZ = `for (let i = 1; i <= 15; i++) {
  if (i % 15 === 0) console.log("FizzBuzz");
  else if (i % 3 === 0) console.log("Fizz");
  else if (i % 5 === 0) console.log("Buzz");
  else console.log(i);
}
`;

async function call(method, path, { token, body } = {}) {
  const response = await fetch(`${API}${path}`, {
    method,
    headers: {
      Accept: "application/json",
      ...(body === undefined ? {} : { "Content-Type": "application/json" }),
      ...(token === undefined ? {} : { Authorization: `Bearer ${token}` }),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await response.text();
  const json = text === "" ? null : JSON.parse(text);
  return { status: response.status, json };
}

function fail(step, result) {
  throw new Error(`${step} failed (${result.status}): ${JSON.stringify(result.json)}`);
}

async function account({ name, email }) {
  const signUp = await call("POST", "/auth/sign-up", { body: { name, email, password: PASSWORD } });
  if (signUp.status === 201) {
    console.log(`  created account ${email}`);
    return signUp.json;
  }
  if (signUp.status !== 409) fail(`sign-up ${email}`, signUp);
  const signIn = await call("POST", "/auth/sign-in", { body: { email, password: PASSWORD } });
  if (signIn.status !== 200) fail(`sign-in ${email}`, signIn);
  console.log(`  signed in as existing ${email}`);
  return signIn.json;
}

async function project(token, spec) {
  const result = await call("POST", "/projects", { token, body: spec });
  if (result.status === 201) console.log(`  created project ${spec.name}`);
  else if (result.json?.code === "project.name-taken") console.log(`  project ${spec.name} exists`);
  else fail(`create project ${spec.name}`, result);
}

async function version(token, projectName, spec) {
  const detail = await call("GET", `/projects/${projectName}`, { token });
  if (detail.json.versions.some((v) => v.name === spec.versionName)) return;
  const result = await call("POST", `/projects/${projectName}/versions`, { token, body: spec });
  if (result.status !== 201) fail(`create version ${spec.versionName}`, result);
  console.log(`  added ${projectName} / ${spec.versionName}`);
}

/**
 * A typing session that appends `source` to the end of `base`: one `create`
 * per character at the cursor, and an `enter` (no auto-indent) per line break,
 * at a human-ish pace. This is exactly what the editor records.
 */
function typingSession(base, source, startedAt) {
  const events = [];
  const baseLines = base.split("\n");
  let time = startedAt;
  let line = baseLines.length - 1;
  let index = baseLines[line].length;
  for (const char of source) {
    time += 40 + ((line * 31 + index * 17) % 90);
    if (char === "\n") {
      events.push({ action: "enter", line, index: 0, code: "", timestamp: time });
      line += 1;
      index = 0;
    } else {
      events.push({ action: "create", line, index, code: char, timestamp: time });
      index += 1;
    }
  }
  return events;
}

async function record(token, projectName, versionNumber, source) {
  const existing = await call("GET", `/projects/${projectName}/versions/${versionNumber}/recording`, { token });
  if (existing.status === 200) {
    console.log(`  ${projectName} v${versionNumber} already recorded`);
    return;
  }
  // A version starts from a file (a template, or the previous version's
  // result); the session is typed on top of it, as it would be in the editor.
  const detail = await call("GET", `/projects/${projectName}`, { token });
  const file = detail.json.versions.find((v) => v.number === versionNumber)?.file;
  const base = file ? await (await fetch(file.url)).text() : "";
  const typed = base === "" || base.endsWith("\n") ? source : `\n${source}`;
  const events = typingSession(base, typed, Date.now() - 10 * 60_000);
  const result = await call("POST", `/projects/${projectName}/versions/${versionNumber}/recording`, {
    token,
    body: { events, code: base + typed },
  });
  if (result.status !== 201) fail(`record ${projectName} v${versionNumber}`, result);
  console.log(`  recorded ${projectName} v${versionNumber} (${events.length} edits)`);
}

async function main() {
  console.log(`Seeding ${API}`);
  const sessions = [];
  for (const spec of ACCOUNTS) sessions.push(await account(spec));
  const [owner, other] = sessions.map((s) => s.accessToken);

  await project(owner, {
    name: "hello-bunny",
    description: "A first recorded session.",
    visibility: "PUBLIC",
    versionName: "version 1",
    fileName: "index.js",
  });
  await record(owner, "hello-bunny", 1, HELLO);

  await project(owner, {
    name: "scratchpad",
    description: "Only its owner can see this one.",
    visibility: "PRIVATE",
    versionName: "version 1",
    fileName: "index.js",
  });

  await project(other, {
    name: "fizz-buzz",
    description: "The classic, typed live.",
    visibility: "PUBLIC",
    versionName: "version 1",
    fileName: "fizz.js",
  });
  await record(other, "fizz-buzz", 1, FIZZ_BUZZ);
  await version(other, "fizz-buzz", { versionName: "version 2", fileName: "fizz.js" });

  // A star each way, so "most starred" has an order. Idempotent on the server.
  await call("PUT", "/projects/fizz-buzz/star", { token: owner });
  await call("PUT", "/projects/hello-bunny/star", { token: other });
  await call("PUT", "/projects/fizz-buzz/star", { token: other });

  console.log("Done. Sign in as domingoos@gmail.com or guest@gmail.com with password test1234.");
}

main().catch((error) => {
  console.error(error.message);
  if (error.cause?.code === "ECONNREFUSED") {
    console.error(`Is the backend running at ${API}?`);
  }
  process.exit(1);
});
