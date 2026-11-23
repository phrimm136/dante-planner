# The settings of terraform/rds in prod; `ops tf plan rds` passes this file and the profile's account id.
# No account id, ARN, hostname or other account-bound value belongs in it.

create_vpc     = true
engine_version = "8.4.11"
multi_az       = false
sql_mode       = "ONLY_FULL_GROUP_BY,STRICT_TRANS_TABLES,NO_ZERO_IN_DATE,NO_ZERO_DATE,ERROR_FOR_DIVISION_BY_ZERO,NO_ENGINE_SUBSTITUTION"
