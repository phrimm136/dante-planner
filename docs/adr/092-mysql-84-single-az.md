# 092 mysql-84-single-az
epic: none · pr: none

## Decisions
- @rds @engine — Both RDS instances (the Oregon primary and the Seoul read replica) run MySQL 8.4. Once RDS standard support for MySQL 8.0 ended, every 8.0 instance carries an Extended Support charge billed per vCPU-hour, independent of instance class; on a db.t4g.micro (2 vCPUs) the charge is roughly ten times the instance price, and one month cost USD 575 in Extended Support against USD 51 of instance hours.
  REJECTED: staying on 8.0 and paying — year-3 pricing doubles the charge, and nothing in the schema or the driver needs 8.0.
  REJECTED: Aurora MySQL or Aurora Serverless v2 — removes the version question for longer but reprices the whole tier upward and would reopen the GTID-replica topology.
- @rds @availability — The primary runs without a Multi-AZ standby. The surcharge disappears either way, so this is a separate question: the database is 25 GB with 7-day PITR, a restore completes in minutes, and the audience is a hobby community, not a paying one; Multi-AZ would also bill the standby's vCPUs.
  REJECTED: keeping Multi-AZ on 8.4 — a standby doubles the instance and storage price to save a few minutes of recovery a few times a year.
- @rds @engine @upgrade — The Seoul replica is upgraded before the primary, because MySQL replication only runs from a lower version to a higher one. A major-version upgrade changes the parameter group family, so each stack carries a new `mysql8.4` group and the instance moves to it in the same apply. `allow_major_version_upgrade` stays on; the pinned `engine_version` is what actually triggers a jump.
- @rds @availability @consequences — The primary's major upgrade is an outage of several minutes with no standby to fail over to, and the weekly maintenance window is a single-instance reboot when RDS patches.

## Takeaway
- takeaway: a managed service's version lifecycle is a price schedule; the end of standard support is a line item that can dwarf the instance, and the notice that announces it reads like routine mail.
