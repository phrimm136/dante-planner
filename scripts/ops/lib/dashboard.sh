# shellcheck shell=bash
# The DantePlanner CloudWatch dashboard (put-dashboard heredoc).

# Create CloudWatch Dashboard, organized by concern (24-col grid).
# Sections are text-widget headers; SEARCH expressions auto-populate widgets for
# collectors that deploy later (procstat) without needing a dashboard edit.
#   System / Host        : CPU/disk/net, memory-vs-alarm, per-process RSS & threads
#   Latency / API        : per-endpoint p50/p90/p99 (nginx logs), HTTP 5xx
#   Database             : local mysqld memory (procstat), Hikari pool + query
#                          digests (prometheus + emit-mysql-query-stats.sh cron)
#                          — goes stale after the RDS cutover removes local mysqld
#   RDS                  : Amazon RDS instance metrics (AWS/RDS namespace) —
#                          CPU/credits, connections, memory, storage, latency, IOPS
#   Spring / Application : error+warn rates, JVM threads, recent error log
#   Alarms               : alarm status strip
setup_dashboard() {
    log_info "Creating CloudWatch dashboard: DantePlanner"

    local ACCOUNT_ID
    ACCOUNT_ID=$(aws sts get-caller-identity --query Account --output text)

    aws cloudwatch put-dashboard \
        --dashboard-name "DantePlanner" \
        --region "$AWS_REGION" \
        --dashboard-body "$(cat <<EOF
{
  "widgets": [
    {
      "type": "text",
      "x": 0, "y": 0, "width": 24, "height": 1,
      "properties": { "markdown": "# System / Host — CPU, memory, disk, network, per-process" }
    },
    {
      "type": "metric",
      "x": 0, "y": 1, "width": 12, "height": 6,
      "properties": {
        "title": "CPU / Disk / Network",
        "metrics": [
          ["$NAMESPACE", "cpu_usage_user",    "InstanceId", "$INSTANCE_ID", "cpu", "cpu-total",          { "label": "CPU %",  "yAxis": "left",  "stat": "Average" }],
          ["$NAMESPACE", "disk_used_percent", "InstanceId", "$INSTANCE_ID", "path", "/", "fstype", "xfs", { "label": "Disk %", "yAxis": "left",  "stat": "Average" }],
          ["$NAMESPACE", "net_bytes_recv",    "InstanceId", "$INSTANCE_ID", "interface", "ens5",          { "label": "Net In (B/5m)",  "yAxis": "right", "stat": "Sum" }],
          ["$NAMESPACE", "net_bytes_sent",    "InstanceId", "$INSTANCE_ID", "interface", "ens5",          { "label": "Net Out (B/5m)", "yAxis": "right", "stat": "Sum" }]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": {
          "left":  { "min": 0, "max": 100, "label": "Percent" },
          "right": { "min": 0, "label": "Bytes/5m" }
        }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 1, "width": 12, "height": 6,
      "properties": {
        "title": "Memory Available vs Low-Memory Alarm",
        "metrics": [
          ["$NAMESPACE", "mem_available", "InstanceId", "$INSTANCE_ID", { "label": "Available", "stat": "Minimum", "color": "#1f77b4" }]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Bytes" } },
        "annotations": { "horizontal": [ { "label": "LowMemory alarm (200 MiB)", "value": 209715200, "color": "#d62728" } ] }
      }
    },
    {
      "type": "metric",
      "x": 0, "y": 7, "width": 12, "height": 6,
      "properties": {
        "title": "Per-Process Memory (RSS)",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"procstat_memory_rss\"', 'Average')", "label": "", "id": "rss" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Bytes" } }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 7, "width": 12, "height": 6,
      "properties": {
        "title": "Per-Process Threads",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"procstat_num_threads\"', 'Average')", "label": "", "id": "thr" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Threads" } }
      }
    },
    {
      "type": "text",
      "x": 0, "y": 13, "width": 24, "height": 1,
      "properties": { "markdown": "# Latency / API — response times, throughput, HTTP status" }
    },
    {
      "type": "log",
      "x": 0, "y": 14, "width": 16, "height": 7,
      "properties": {
        "title": "Slowest Endpoints — p50 / p90 / p99 (nginx request_time, SSE excluded)",
        "query": "SOURCE '/ecs/danteplanner/nginx' | filter ispresent(request_time) | filter uri not like \"/events\" and uri not like \"/api/sse/\" | stats pct(request_time, 50) as p50, pct(request_time, 90) as p90, pct(request_time, 99) as p99, count(*) as reqs by uri | sort p99 desc | limit 20",
        "region": "$AWS_REGION",
        "view": "table"
      }
    },
    {
      "type": "metric",
      "x": 16, "y": 14, "width": 8, "height": 7,
      "properties": {
        "title": "HTTP 5xx Rate",
        "metrics": [
          ["$NAMESPACE", "HTTP5xxCount", { "color": "#9467bd", "label": "HTTP 5xx" }]
        ],
        "period": 60,
        "stat": "Sum",
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0 } }
      }
    },
    {
      "type": "log",
      "x": 0, "y": 21, "width": 12, "height": 6,
      "properties": {
        "title": "Request Rate (req / min)",
        "query": "SOURCE '/ecs/danteplanner/nginx' | stats count(*) as requests by bin(1m)",
        "region": "$AWS_REGION",
        "view": "timeSeries"
      }
    },
    {
      "type": "log",
      "x": 12, "y": 21, "width": 12, "height": 6,
      "properties": {
        "title": "HTTP Status Distribution",
        "query": "SOURCE '/ecs/danteplanner/nginx' | stats count(*) as n by bin(5m), status",
        "region": "$AWS_REGION",
        "view": "timeSeries"
      }
    },
    {
      "type": "text",
      "x": 0, "y": 27, "width": 24, "height": 1,
      "properties": { "markdown": "# Database — MySQL memory, connection pool & query performance" }
    },
    {
      "type": "metric",
      "x": 0, "y": 28, "width": 12, "height": 6,
      "properties": {
        "title": "MySQL Process Memory (RSS)",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"procstat_memory_rss\" exe=\"mysqld\"', 'Average')", "label": "mysqld", "id": "dbrss" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Bytes" } }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 28, "width": 12, "height": 6,
      "properties": {
        "title": "Connection Pool (HikariCP)",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"hikaricp_connections_active\"', 'Average')", "label": "active", "id": "pa" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"hikaricp_connections_pending\"', 'Average')", "label": "pending", "id": "pp" } ]
        ],
        "period": 60,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Connections" } }
      }
    },
    {
      "type": "metric",
      "x": 0, "y": 34, "width": 24, "height": 6,
      "properties": {
        "title": "Query Latency & Lock Time by Statement (perf_schema digests)",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_query_latency_avg_ms\"', 'Average')", "label": "", "id": "ql" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_query_lock_avg_ms\"', 'Average')", "label": "", "id": "qlk", "yAxis": "right" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "latency ms" }, "right": { "min": 0, "label": "lock ms" } }
      }
    },
    {
      "type": "metric",
      "x": 0, "y": 40, "width": 12, "height": 6,
      "properties": {
        "title": "InnoDB Lock Contention",
        "metrics": [
          [ { "expression": "RATE(SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_innodb_deadlocks\"', 'Maximum'))*300", "label": "deadlocks /5m", "id": "dl" } ],
          [ { "expression": "RATE(SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_innodb_lock_timeouts\"', 'Maximum'))*300", "label": "lock timeouts /5m", "id": "lt" } ],
          [ { "expression": "RATE(SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_innodb_row_lock_waits\"', 'Maximum'))*300", "label": "row-lock waits /5m", "id": "rlw" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_innodb_row_lock_time_avg_ms\"', 'Average')", "label": "row-lock time avg (ms)", "id": "rlt", "yAxis": "right" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "events / 5m" }, "right": { "min": 0, "label": "ms" } }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 40, "width": 12, "height": 6,
      "properties": {
        "title": "Transactions & Blocking",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_blocked_sessions\"', 'Maximum')", "label": "blocked sessions", "id": "bs" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_active_transactions\"', 'Maximum')", "label": "active trx", "id": "at" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_threads_running\"', 'Maximum')", "label": "threads running", "id": "trn" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"mysql_longest_transaction_s\"', 'Maximum')", "label": "longest trx (s)", "id": "ltx", "yAxis": "right" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Count" }, "right": { "min": 0, "label": "Seconds" } }
      }
    },
    {
      "type": "text",
      "x": 0, "y": 46, "width": 24, "height": 1,
      "properties": { "markdown": "# RDS (Amazon RDS MySQL) — instance CPU/credits, connections, memory, storage, latency, IOPS (AWS/RDS namespace)" }
    },
    {
      "type": "metric",
      "x": 0, "y": 47, "width": 12, "height": 6,
      "properties": {
        "title": "RDS CPU % & CPU Credit Balance",
        "metrics": [
          ["AWS/RDS", "CPUUtilization",   "DBInstanceIdentifier", "$RDS_INSTANCE_ID", { "label": "CPU %",       "yAxis": "left",  "stat": "Average" }],
          ["AWS/RDS", "CPUCreditBalance", "DBInstanceIdentifier", "$RDS_INSTANCE_ID", { "label": "CPU credits", "yAxis": "right", "stat": "Average" }]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "max": 100, "label": "Percent" }, "right": { "min": 0, "label": "Credits" } }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 47, "width": 12, "height": 6,
      "properties": {
        "title": "RDS Connections & Freeable Memory",
        "metrics": [
          ["AWS/RDS", "DatabaseConnections", "DBInstanceIdentifier", "$RDS_INSTANCE_ID", { "label": "connections",  "yAxis": "left",  "stat": "Average" }],
          ["AWS/RDS", "FreeableMemory",       "DBInstanceIdentifier", "$RDS_INSTANCE_ID", { "label": "freeable mem", "yAxis": "right", "stat": "Average" }]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Connections" }, "right": { "min": 0, "label": "Bytes" } }
      }
    },
    {
      "type": "metric",
      "x": 0, "y": 53, "width": 12, "height": 6,
      "properties": {
        "title": "RDS Read / Write Latency",
        "metrics": [
          ["AWS/RDS", "ReadLatency",  "DBInstanceIdentifier", "$RDS_INSTANCE_ID", { "label": "read",  "stat": "Average" }],
          ["AWS/RDS", "WriteLatency", "DBInstanceIdentifier", "$RDS_INSTANCE_ID", { "label": "write", "stat": "Average" }]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Seconds" } }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 53, "width": 12, "height": 6,
      "properties": {
        "title": "RDS IOPS & Queue Depth",
        "metrics": [
          ["AWS/RDS", "ReadIOPS",       "DBInstanceIdentifier", "$RDS_INSTANCE_ID", { "label": "read IOPS",   "yAxis": "left",  "stat": "Average" }],
          ["AWS/RDS", "WriteIOPS",      "DBInstanceIdentifier", "$RDS_INSTANCE_ID", { "label": "write IOPS",  "yAxis": "left",  "stat": "Average" }],
          ["AWS/RDS", "DiskQueueDepth", "DBInstanceIdentifier", "$RDS_INSTANCE_ID", { "label": "queue depth", "yAxis": "right", "stat": "Average" }]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "IOPS" }, "right": { "min": 0, "label": "Queue depth" } }
      }
    },
    {
      "type": "metric",
      "x": 0, "y": 59, "width": 12, "height": 6,
      "properties": {
        "title": "RDS Free Storage Space",
        "metrics": [
          ["AWS/RDS", "FreeStorageSpace", "DBInstanceIdentifier", "$RDS_INSTANCE_ID", { "label": "free storage", "stat": "Average" }]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Bytes" } },
        "annotations": { "horizontal": [ { "label": "Low storage (2 GiB)", "value": 2147483648, "color": "#d62728" } ] }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 59, "width": 12, "height": 6,
      "properties": {
        "title": "RDS Read / Write Throughput",
        "metrics": [
          ["AWS/RDS", "ReadThroughput",  "DBInstanceIdentifier", "$RDS_INSTANCE_ID", { "label": "read B/s",  "stat": "Average" }],
          ["AWS/RDS", "WriteThroughput", "DBInstanceIdentifier", "$RDS_INSTANCE_ID", { "label": "write B/s", "stat": "Average" }]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Bytes/s" } }
      }
    },
    {
      "type": "text",
      "x": 0, "y": 65, "width": 24, "height": 1,
      "properties": { "markdown": "# Spring / Application — errors, JVM, throughput" }
    },
    {
      "type": "metric",
      "x": 0, "y": 66, "width": 12, "height": 6,
      "properties": {
        "title": "Error & Warning Rates",
        "metrics": [
          ["$NAMESPACE", "BackendErrorCount", { "color": "#d62728", "label": "Errors" }],
          ["$NAMESPACE", "BackendWarnCount",  { "color": "#ff7f0e", "label": "Warnings" }]
        ],
        "period": 60,
        "stat": "Sum",
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0 } }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 66, "width": 12, "height": 6,
      "properties": {
        "title": "JVM Heap Memory (used / committed / max)",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"jvm_memory_used_bytes\" area=\"heap\"', 'Average')", "label": "used", "id": "hu" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"jvm_memory_committed_bytes\" area=\"heap\"', 'Average')", "label": "committed", "id": "hc" } ],
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"jvm_memory_max_bytes\" area=\"heap\"', 'Average')", "label": "max", "id": "hm" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Bytes" } }
      }
    },
    {
      "type": "metric",
      "x": 0, "y": 72, "width": 12, "height": 6,
      "properties": {
        "title": "JVM GC Pause",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"jvm_gc_pause_seconds_max\"', 'Average')", "label": "", "id": "gc" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Seconds" } }
      }
    },
    {
      "type": "metric",
      "x": 12, "y": 72, "width": 12, "height": 6,
      "properties": {
        "title": "Backend JVM Threads",
        "metrics": [
          [ { "expression": "SEARCH('Namespace=\"$NAMESPACE\" MetricName=\"procstat_num_threads\" exe=\"java\"', 'Average')", "label": "java", "id": "jvmthr" } ]
        ],
        "period": 300,
        "region": "$AWS_REGION",
        "view": "timeSeries",
        "yAxis": { "left": { "min": 0, "label": "Threads" } }
      }
    },
    {
      "type": "log",
      "x": 0, "y": 78, "width": 24, "height": 8,
      "properties": {
        "title": "Recent Backend Errors & Warnings",
        "query": "SOURCE '/ecs/danteplanner/backend' | fields @timestamp, level, logger_name, message, method, path, userId, thread_name | filter level = \"ERROR\" or level = \"WARN\" | sort @timestamp desc | limit 50",
        "region": "$AWS_REGION",
        "view": "table"
      }
    },
    {
      "type": "text",
      "x": 0, "y": 86, "width": 24, "height": 1,
      "properties": { "markdown": "# Alarms" }
    },
    {
      "type": "alarm",
      "x": 0, "y": 87, "width": 24, "height": 4,
      "properties": {
        "title": "Alarm Status",
        "alarms": [
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HighCPU",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-LowMemory",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HighDisk",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HighNetworkIn",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HighNetworkOut",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HighDiskIOWrite",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HTTP5xx",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-BackendErrors",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-BackendWarnings",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-BackendSilence",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-HTTP429",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-TrafficDrop",
          "arn:aws:cloudwatch:$AWS_REGION:$ACCOUNT_ID:alarm:DantePlanner-CloudflareIpSilence"
        ]
      }
    }
  ]
}
EOF
)"

    log_info "Dashboard created: DantePlanner"
    log_info "View: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner"
}
