# Dante's Planner

Planning and deck-building tools for Limbus Company. Live at [dante-planner.com](https://dante-planner.com).

## What it is

Dante's Planner assists Limbus Company players throughout the game — planning a Mirror Dungeon run before starting it, looking up Identities, E.G.O and keywords outside the game, or estimating pull rates for the next pickup. Plans stay in your browser first, and sync to your account when you need them on another device.

## Features

- Mirror Dungeon planner that fully mirrors the flow of each run with deck-code import/export
- Identity, E.G.O, E.G.O gift, status keyword, dungeon event, and theme-pack browsers with in-game-accurate UI components
- Local-first editing: Plans are saved to your browser and sync to your account when you need. Back up the local plans as an alternative
- Publish plans and communicate to other players
- Pull rate calculator to prepare upcoming pickups
- Available in English, 한국어, 日本語, and 中文

## Repository layout

Monorepo: `frontend/` (React 19 + TypeScript, Vite), `backend/` (Spring Boot 3.5, MySQL), `static/` (game data submodule).

```
                                browser
                                   │ every request           ▲ SSE
                                   ▼                         │
       ┌────────────────── Cloudflare edge ──────────────────┴────┐
       │                      CDN · WAF                           │
       │           host = api.dante-planner.com ?                 │
       └────────┬─────────────────────────────────┬───────────────┘
             no │                             yes │           ▲
                ▼                                 ▼           │
┌── Cloudflare Pages ───┐    ┌── AWS EC2 ─────────────────────┼───────────┐
│  React SPA            │    │  nginx ──▶ Spring Boot ── SSE ─┘           │
│  game assets          │    │               │  (plan sync · comments     │
└───────────────────────┘    │               │   · notifications)         │
 ◀ frontend/, static/        │               ▼                            │
                             │             MySQL ── nightly dump ──▶ S3   │
                             │                                            │
                             │  logs · metrics ──▶ CloudWatch · Sentry    │
                             └────────────────────────────────────────────┘
                              ◀ backend/
```

## Operations

All traffic enters through the Cloudflare edge: `api.dante-planner.com` is proxied to an AWS EC2 origin (nginx, Spring Boot, MySQL); everything else is served by Cloudflare Pages. Deploys go through GitHub Actions: changed services are detected against the last release tag, built to ECR, rolled out over AWS SSM (no SSH exposure), health-checked, and rolled back automatically on failure. Monitored with CloudWatch and Sentry; load-tested with k6.

Planned: multi-region k3s with a causal-consistency read path — lives in [`docs/tasks/034-multi-region-k8s-architecture/`](docs/tasks/034-multi-region-k8s-architecture/).

## Testing

Backend: 1,000+ JUnit tests including ArchUnit suites that enforce layer and feature-package boundaries. Frontend: ~6,000 Vitest cases across 128 files. CI replays Flyway migrations against a fresh MySQL container on every backend change.

## License

[AGPL-3.0](LICENSE). This is an unofficial fan project — Limbus Company and all related assets belong to Project Moon.
