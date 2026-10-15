---
name: backend
description: Build or change the Bunny Code API — Spring Boot 4 / Java 25 modular monolith with JPA (Hibernate 7) on MySQL, Redis, InfluxDB, S3, STOMP WebSockets and the Docker code sandbox — and its JUnit, Testcontainers and Cucumber tests. Use this skill whenever work touches src/main/java, src/test, pom.xml, Flyway migrations or application*.yml, including "add an endpoint", "add a STOMP destination", "add a column", "why is this query slow", "this returns 500", or "write a test for this" — even when the request doesn't name a layer.
---

# Bunny Code backend (v2)

Spring Boot 4.1 · Java 25 · Maven wrapper. Run everything with `./mvnw verify`; run the app locally
with every dependency in containers via `./mvnw spring-boot:test-run -Dspring-boot.run.profiles=local`
(Docker must be running). `docs/MIGRATION_PLAN.md` records why things are the way they are.

## Modules (Spring Modulith — enforced by `ModularityTest`)

```
shared      OPEN kernel: BunnyException/ErrorKind, ProblemDetail advice, PageResponse, LikePatterns,
            @ResourceName, security (JWT, cookie, CSRF), STOMP config, RateLimiter, Clock
identity    users, sign-up/in. API: UserDirectory, UserSummary
storage     S3. API: FileStorage, StorageLayout (the only place that builds storage keys)
project     projects, versions, files, recordings (+ InfluxDB events). API: ProjectAccess
execution   sandbox. API: CodeExecutor, ExecutionResult (sealed)
workspace   edit lock (Redis) over STOMP
battle      invitations, live battles, judging over STOMP + lobby REST
```

A module may only use another module's **base package** (its API). Reference other modules' data by
id and resolve display data with one batched call (`UserDirectory.findAllByIds`), never per row.
Inside each module: `api` (controllers, request records) → `application` (services, ports, views)
→ `domain` (entities, repositories, rules). `infrastructure` implements ports. `ArchitectureTest`
enforces the direction; domain never imports web/messaging.

## Rules that matter here

- **JPA without surprises.** All to-one associations `LAZY`; no inverse `@OneToOne`. Lists and
  details read **DTO projections** (JPQL `select new …`), not entity graphs. Counters and state
  changes are atomic `UPDATE`s (`@Modifying`) — battle transitions are compare-and-set and the caller
  acts only when it got `1`. `open-in-view` is off. Soft delete is `@SQLRestriction("deleted = 0")`
  (Hibernate forbids lazy to-one into `@SoftDelete` entities).
- **Prove there is no N+1.** Repository/service tests count statements with `support/QueryCounter`
  (Hibernate statistics) and assert the count is the same for 1 row and for many.
- **Schema = Flyway.** `V1` is the frozen production baseline (adopted with baseline-on-migrate);
  add `V{n}__*.sql`, never edit applied ones. `ddl-auto=validate` fails startup on any mapping drift.
  MySQL `int unsigned` columns are mapped with `columnDefinition = "int unsigned"`.
- **Errors.** Throw `BunnyException.<kind>(code, userMessage)`; HTTP maps it to a problem detail,
  STOMP to `/user/queue/errors`. User text in the message, operator detail in logs only.
- **Validation.** Request records with Bean Validation (`@ResourceName`, `@Size`, …); controller
  params get method validation automatically. Domain constructors re-check invariants.
- **Security.** Routes are declared in `shared/security/SecurityConfiguration`; data-dependent rules
  (ownership, private projects → 404) live in services. Use `@CurrentUser AuthenticatedUser` (or
  `Optional<…>` for public endpoints). Cookie-authenticated writes go through the dedicated CSRF
  filter — the resource server would otherwise exempt them.
- **Sandbox = security boundary.** No shell strings: `ProcessRunner` takes argument vectors; user code
  goes over stdin; container names are generated. Limits (`--cpus 0.2 --memory 20m`, 10 s) plus
  `--network none --read-only --cap-drop ALL --pids-limit`. Never relax them to make code pass.
- **Long work off the WebSocket thread.** Judging runs on `JudgeDispatcher` (virtual threads,
  bounded); keystroke traffic touches Redis only.
- **Effective Java defaults.** Constructor injection only, records for values, sealed types for
  closed hierarchies, `Clock` for time, immutable collections, no secrets in `toString()`.

## Tests (all part of `./mvnw verify`)

| Kind | Where / naming | Notes |
|---|---|---|
| Unit | `*Test`, Mockito BDD style, `@Nested` + `@DisplayName` Given/When/Then | no Spring |
| Web slice | `*ControllerTest`, `@WebMvcTest` + `WebSecurityTestConfiguration` | real security chain, mint tokens with `TestTokens` |
| JPA / Redis / Influx / S3 / Docker | `*IT` | Testcontainers singletons in `support/TestContainers` (not beans — Spring would stop them) |
| BDD | `src/test/resources/features/*.feature`, glue in `com.bunnycode.bdd`, runner `RunCucumberIT` | full app on a random port; HTTP via `ApiClient`, STOMP via `StompTestSession`; `World` is scenario-scoped (use its methods, never fields) |

JaCoCo merges unit + IT coverage and fails the build under 85% lines.

## API contract

`references/api-surface.md` is the contract the frontend builds against. Update it in the same change
as any controller or STOMP destination, and add/adjust the matching Cucumber scenario.
