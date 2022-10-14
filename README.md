# Bunny Code

> A brand new coding website. Supports replaying coding process and launching a coding battle. For more information, please check out [Bunny Code](https://www.domingoos.store).

## Table of content

- [Background and Why Bunny Code](#background-and-why-bunny-code)
- [Links](#links)
- [Demo](#demo)
- [Features and Tech-stacks](#features-and-tech-stacks)
- [System architechture](#system-architechture)
- [Future Features](#future-features)

## Background and Why Bunny Code

Being a software engineer, we learn continuously to enrich ourselves. However, there are no integrated resources where we could learn from other people's `coding process` and edit our own project at the same time. Or even `battle with each other`.

Bunny Code was borned to cope with the problem. Bunny Code is dedicated to help users learn by replaying coding processes as if vedio clips and practice by launching live battles. With Bunny Code, users can `edit codes`, `run codes`, `control versions`, `replaying codes`, `launches battle` whenever they want !

## Links

**Bunny Code Homepage**: [Bunny Code](https://domingoos.store)

```
// first user
Accound: domingo0204@gmail.com
Password: test123456

// second user
Accound: guest@gmail.com
Password: test1234
```

**Frontend Github repo**: [Bunny-code-vue](https://github.com/domingo1021/Bunny-code-vue)

Bunny Code frontend separation project, developed with Vue.js frontend framework.

## Demo

gif

## Features and Tech stacks

- Record and replaying user coding processes: `InfluxDB (time series database)`
- Coding Version control mechanism: `MySQL schema design`
- Coding sandbox, running codes in separate namespace with limited resources: `Docker`
- Arrange Sandbox jobs to different server: `Prometheus exporter` `Shell script`
- Cache and real-time synchronized coding battle: `Socket.IO` `Redis`
- Host static files (including coding archives) in stateless way: `S3`
- Speed up website static files services: `CloudFront (CDN)`
- Frontend SPA: `Vue 3 Composition API`
- Continuously develope & automatic CDN cache invalidation: `GitHub Actions`
- Gather project logs to improve application matainability: `CloudWatch`

## System architechture

![System architecture](./Archives//System_architecture.png)

## Future Features
