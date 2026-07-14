---
uid: 01KXBE888RGHAYZY2BJ68ZZAR7
created: 2026-07-12T15:13:39Z
updated: 2026-07-12T15:24:11Z
scope: global
category: gotcha
origin: draft
title: Self-managed k3s does not wire private ECR image-pull auth by default. The kubelet fails with 'no basic auth credentials
status: active
attributed_to: llm
entities: [k3s, AWS]
hash: 3aa35c04
source: text
---

Self-managed k3s does not wire private ECR image-pull auth by default. The kubelet fails with 'no basic auth credentials' even though the node's IAM instance profile has ECR read permission, because the kubelet has no credential-provider configured to use that identity. Fix: install the cloud-provider-aws ecr-credential-provider binary, create a CredentialProviderConfig, and pass --kubelet-arg=image-credential-provider-config and --image-credential-provider-bin-dir in node user-data. IAM permission alone is insufficient; the kubelet must know how to use it.
