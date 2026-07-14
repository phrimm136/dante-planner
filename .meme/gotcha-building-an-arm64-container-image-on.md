---
uid: 01KXBE88CW7QPCR1TGGY86WX9R
created: 2026-07-12T15:13:39Z
updated: 2026-07-12T15:24:12Z
scope: global
category: gotcha
origin: draft
title: Building an arm64 container image on an amd64 runner executes the entire Dockerfile under slow QEMU emulation. To optimi
status: active
attributed_to: llm
entities: [Docker]
hash: faaef16d
source: text
---

Building an arm64 container image on an amd64 runner executes the entire Dockerfile under slow QEMU emulation. To optimize: use FROM --platform=$BUILDPLATFORM on the builder stage to build architecture-neutral artifacts (e.g., Java jar) natively; only the final runtime stage (e.g., JRE) needs the target arm64 platform. Note: the docker build context sends the entire directory tree (minus .dockerignore) to the daemon, but only COPY/ADD statements land in the image.
