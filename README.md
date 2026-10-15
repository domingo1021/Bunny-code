# Bunny Code

> A brand new coding website. Supports replaying coding process and launching a coding battle. For more information, please check out [Bunny Code](https://www.domingoos.store).

## Table of content

- [Background and Why Bunny Code](#background-and-why-bunny-code)
- [Links](#links)
- [Demo](#demo)
- [Features - Workspace](#features---workspace)
- [Features - Battle Valley](#features---battle-valley)
- [Tech Stack](#tech-stack)
- [Development](#development)
- [System Architecture](#system-architecture)
- [Future Features](#future-features)

## Background and Why Bunny Code

Being a software engineer, we learn continuously to enrich ourselves. However, there are no integrated resources where we could learn from other people's `coding processes` and edit our own project at the same time. Not to mention `battle with each other`.

Bunny Code was borned to cope with the problem. Bunny Code is dedicated to help users learn by replaying coding processes as if video clips and practice by launching live battles. With Bunny Code, users can `edit codes`, `run codes`, `control versions`, `replay codes`, `launch battles` whenever they want !

## Links

```
// first user
Account: domingoos@gmail.com
Password: test1234

// second user (for battle)
Account: guest@gmail.com
Password: test1234
```

**Frontend Repo**: [bunny-code-web](https://github.com/domingo1021/bunny-code-web)

**Backend Repo**: [bunny-code-server](https://github.com/domingo1021/bunny-code-server)

## Features - Workspace

- **Record** and **replay** users' projects in workspace
- Provide **play**, **pause**, **speed up** and **slow down** to help user to replay coding processes as if watching video clips.
- **Preview coding archives** of projects at the first time users go into the workspace.
- Provide various **shortcuts** to help developers edit project much faster.
- Allow user **run codes** immediately after they edit codes or watch codes.
- **Create version** from latest coding process and provide **version control**. Help users compare differences between version by replaying projects' coding processes.

## Features - Battle Valley

- **Choose a question level**, **launch a battle**, then user can compete with others in battle valley.
- **Watch a live battle** in battle valley as long as user want.
- **Run codes** to check the answer of the appointed question.
- Compare **expected outputs** and **battler coding outputs** for test cases, help battlers to improve their codes.
- **Share winner coding archives** of battles to help users learn from each other.

## Demo

- Replay coding process ( x1 )

  ![image](./Archives/Replay-x1.gif)

- Replay coding process ( x2 )

  ![image](./Archives/Replay-x2.gif)

- Run code (Normal situation)

  ![image](./Archives/Run-code.gif)

- Run code (Out of memory)

  ![image](./Archives/OOM.gif)

- Battle valley

  ![image](./Archives/Battle-valley.gif)

## Tech stack

- Frontend: `Next.js` + `TypeScript`
- Backend: `Java 25` `Spring Boot 4.1` `Spring Data JPA / Hibernate 7` `Spring Security (JWT)` `STOMP over WebSocket` `Spring Modulith`
- Database: `MySQL` (Flyway migrations) `InfluxDB` `Redis`
- Sandbox: `Docker` (no network, read-only, 0.2 CPU / 20 MB / 10 s) `node-exporter`
- Cloud services: `S3` `CloudFront`
- Testing: `JUnit 5` `Mockito` `AssertJ` `ArchUnit` `Cucumber (BDD)` `Testcontainers` `JaCoCo`
- Continuous integration: `GitHub Actions`

## Development

Requirements: JDK 25 and a running Docker daemon (tests use Testcontainers).

```bash
./mvnw verify                                                    # all tests + coverage gate
docker compose -f Docker/local/compose.yaml up -d --wait         # MySQL, Redis, InfluxDB, S3 on localhost
./mvnw spring-boot:run -Dspring-boot.run.profiles=local          # API on :8080
node Docker/local/seed.mjs                                       # demo accounts and projects
```

## System architecture

```mermaid
flowchart TB
    dev(["Developer"])
    user(["End user · browser"])

    subgraph github["GitHub"]
        direction LR
        feRepo["<b>Frontend repo</b> · Bunny-code-vue<br/>Next.js 16 · React 19 · TypeScript"]
        beRepo["<b>Backend repo</b> · Bunny-code<br/>Spring Boot 4.1 · Java 25"]
        actions["<b>GitHub Actions</b><br/>verify · build images · deploy"]
    end

    subgraph server["Application server · Docker Compose"]
        nginx["<b>nginx</b><br/>TLS · reverse proxy · WebSocket upgrade"]
        web["<b>Next.js server</b><br/>server-rendered pages + React client"]
        api["<b>Spring Boot API</b> · modular monolith<br/>REST /api/v2 · STOMP /api/v2/ws<br/>identity · project · workspace · execution · battle"]
        mysql[("<b>MySQL 8.4</b><br/>users · projects · versions<br/>battles · questions")]
        redis[("<b>Redis</b><br/>live battles · invitations<br/>edit locks · rate limits")]
        influx[("<b>InfluxDB 2.7</b><br/>recorded edit events")]
    end

    subgraph aws["AWS"]
        s3[("<b>S3 bucket</b><br/>code files · questions<br/>avatars · winners' code")]
        cdn["<b>CloudFront CDN</b>"]
    end

    subgraph sandboxes["Coding sandbox hosts (1..n)"]
        sandbox["<b>Docker + Node.js</b><br/>throw-away container per run<br/>no network · 0.2 CPU · 20 MB · 10 s"]
        exporter["<b>node_exporter</b> :9100<br/>CPU / memory metrics"]
    end

    dev -- "git push" --> feRepo & beRepo
    feRepo & beRepo --> actions
    actions -- "deploy web + api images" --> nginx

    user -- "HTTPS: pages · REST · STOMP" --> nginx
    nginx -- "/" --> web
    nginx -- "/api/v2 + WebSocket" --> api
    web -- "REST (server-side rendering)" --> api

    api --> mysql & redis & influx
    api -- "upload files" --> s3
    s3 -- "origin" --> cdn
    user -- "HTTPS: download files<br/>replays · questions · avatars" --> cdn

    api -- "SSH: docker run" --> sandbox
    api -. "probe load, pick the least busy host" .-> exporter
```

## Future Features

- More `shortcuts` in workspace. For example: `redo`, `undo`, and etc.
- `Friend` and `follower` mechanism. Help users invite battles to a target person.
