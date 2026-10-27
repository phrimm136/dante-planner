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

Monorepo: `frontend/` (React 19 + TypeScript, Vite), `backend/` (Spring Boot 3.5, MySQL 8.4), `static/` (symlink to the game-data checkout, pinned by `static.lock`), `deploy/` (k3s manifests that ArgoCD syncs), `terraform/` (AWS and Cloudflare), `e2e/` (Playwright).

```
                                   browser
                                      │                       ▲ SSE (comments · notifications)
          ┌──────────────── Cloudflare edge · CDN · WAF ──────┴─────────────────────┐
          │ dante-planner.com                              api.dante-planner.com    │
          └──────┬──────────────────────────────────────────────┬───────────────────┘
                 ▼                                              ▼
 ┌── Cloudflare Pages ──┐                   Cloudflare Load Balancer · geo steering
 │ React SPA            │                     Asia-Pacific │           │ everyone else
 │ hashed game assets   │                                  ▼           ▼
 └──────────────────────┘            ┌── k3s · Seoul ─────────┐ ┌── k3s · Oregon ────────┐
  ◀ frontend/, static/               │ Cloudflare Tunnel      │ │ Cloudflare Tunnel      │
                                     │ Traefik ─▶ Spring Boot │ │ Traefik ─▶ Spring Boot │
                                     │ Redis (replica)        │ │ Redis                  │
                                     └──┬──────────────┬──────┘ └───────────┬────────────┘
                                  reads │       writes └────────────────────┤
                                        ▼                                   ▼
                              RDS MySQL read replica ◀─── replication ─── RDS MySQL primary

   both regions: metrics ─▶ Prometheus ─▶ Grafana Cloud · logs ─▶ Alloy ─▶ Loki · errors ─▶ Sentry
                                                                       ◀ backend/, deploy/, terraform/
```

Design decisions are recorded in [`docs/adr/`](docs/adr/).

## Data and credits

Game data, text and images are extracted from the Limbus Company client and belong to Project Moon. The Chinese translation is provided by [LocalizeLimbusCompany](https://github.com/LocalizeLimbusCompany/LocalizeLimbusCompany).

## License

[AGPL-3.0](LICENSE). This is an unofficial fan project — Limbus Company and all related assets belong to Project Moon.
