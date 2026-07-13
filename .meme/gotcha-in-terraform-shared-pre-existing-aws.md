---
uid: 01KXBE887BPC0XA1JQDTEEJ03S
created: 2026-07-12T15:13:39Z
updated: 2026-07-12T15:23:53Z
scope: global
category: gotcha
origin: draft
title: In Terraform, shared pre-existing AWS resources (e.g., ECR repositories shared across multiple stacks) must be modeled a
status: active
attributed_to: llm
entities: [Terraform, AWS]
hash: e99ae0c2
source: text
---

In Terraform, shared pre-existing AWS resources (e.g., ECR repositories shared across multiple stacks) must be modeled as a data source, not a resource. As a resource, terraform destroy attempts deletion (blocked only by guards like not-empty), and the next apply hits RepositoryAlreadyExistsException, forcing manual terraform import. Using a data source makes destroy+apply rebuild unattended and reproducible.
