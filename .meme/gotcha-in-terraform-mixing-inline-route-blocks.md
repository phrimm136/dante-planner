---
uid: 01KXBE8882Q1VV6SRPK2SWVH74
created: 2026-07-12T15:13:39Z
updated: 2026-07-12T15:24:11Z
scope: global
category: gotcha
origin: draft
title: In Terraform, mixing inline route {} blocks inside aws_route_table with standalone aws_route resources on the same table
status: active
attributed_to: llm
entities: [Terraform, AWS]
hash: 3bf2543f
source: text
---

In Terraform, mixing inline route {} blocks inside aws_route_table with standalone aws_route resources on the same table causes them to clobber each other on every plan — the inline block asserts it owns the entire route set and deletes standalone routes as drift. Fix: make all routes standalone aws_route resources. The same clobber trap applies to inline security group rules vs standalone aws_vpc_security_group_ingress_rule.
