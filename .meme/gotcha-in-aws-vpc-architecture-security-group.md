---
uid: 01KXBE88DBZ5Y55QEWRHMTYZJQ
created: 2026-07-12T15:13:39Z
updated: 2026-07-12T15:24:12Z
scope: global
category: gotcha
origin: draft
title: In AWS VPC architecture, security group rules cannot reference resources across VPC boundaries. Reaching a private resou
status: active
attributed_to: llm
entities: [AWS, Terraform]
hash: a8999ebf
source: text
---

In AWS VPC architecture, security group rules cannot reference resources across VPC boundaries. Reaching a private resource in a peered VPC requires: (1) VPC peering connection, (2) a route on BOTH VPCs' route tables directing traffic over the peering, and (3) an ingress security group rule on the target resource's SG that references the source SG (must be from the same VPC). For cross-region peering: auto_accept=true works only same-account and same-region; cross-region requires an explicit aws_vpc_peering_connection_accepter in the peer region.
