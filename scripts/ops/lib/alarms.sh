# shellcheck shell=bash
# Alarm thresholds + all CloudWatch metric alarms.

# Alarm thresholds — these determine when you get alerted. Consider:
# - CPU: t3.small can burst to 20% baseline, 70% is conservative
# - Memory: 2GB total, 200MB free is tight but catches OOM early
# - Disk: 85% leaves buffer for logs/temp files
# - 5xx errors: 10 in 5min might be too sensitive for low-traffic periods
get_alarm_thresholds() {
    # CPU% MEM_BYTES DISK% HTTP5xx NET_IN_BYTES NET_OUT_BYTES DISKIO_WRITE_BYTES
    # Network/diskio thresholds are 5-minute Sum totals.
    # Tuned for t3.medium serving JSON API only (static assets on Cloudflare),
    # ~500 DAU / 100 peak concurrent. Revisit after observing baselines.
    echo "70 209715200 85 10 52428800 209715200 524288000"
}

# Create CloudWatch alarms
create_alarms() {
    local TOPIC_ARN="$1"

    # Get thresholds from configuration function
    read CPU_THRESHOLD MEM_THRESHOLD DISK_THRESHOLD HTTP5XX_THRESHOLD NET_IN_THRESHOLD NET_OUT_THRESHOLD DISKIO_WRITE_THRESHOLD <<< $(get_alarm_thresholds)

    log_info "Creating alarms with thresholds: CPU>${CPU_THRESHOLD}%, Mem<${MEM_THRESHOLD}B, Disk>${DISK_THRESHOLD}%, NetIn>${NET_IN_THRESHOLD}B/5m, NetOut>${NET_OUT_THRESHOLD}B/5m, DiskIOWrite>${DISKIO_WRITE_THRESHOLD}B/5m, 5xx>${HTTP5XX_THRESHOLD}"

    # Alarm 1: High CPU Usage
    # cpu=cpu-total dimension required — CW Agent publishes aggregated series under this key when totalcpu: true
    log_info "Creating High CPU alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HighCPU" \
        --alarm-description "CPU > ${CPU_THRESHOLD}% for 5 min. Check per-process CPU (java/mysqld) and Request Rate for a traffic spike or runaway loop. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "cpu_usage_user" \
        --dimensions Name=InstanceId,Value="$INSTANCE_ID" Name=cpu,Value=cpu-total \
        --statistic Average \
        --period 300 \
        --threshold "$CPU_THRESHOLD" \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 2: Low Memory
    # 3-of-3 (15 min sustained) to stop threshold flapping — mem_available oscillates
    # right at the 200 MiB line, so a 1-of-1 evaluation pages every few minutes.
    log_info "Creating Low Memory alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-LowMemory" \
        --alarm-description "mem_available < 200 MiB sustained 15 min (3x5m). Likely the traffic-correlated JVM off-heap growth — check Per-Process RSS + JVM Heap panels and correlate with Request Rate. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "mem_available" \
        --dimensions Name=InstanceId,Value="$INSTANCE_ID" \
        --statistic Average \
        --period 300 \
        --threshold "$MEM_THRESHOLD" \
        --comparison-operator LessThanThreshold \
        --evaluation-periods 3 \
        --datapoints-to-alarm 3 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data breaching \
        --region "$AWS_REGION"

    # Alarm 3: Disk Usage High
    log_info "Creating High Disk Usage alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HighDisk" \
        --alarm-description "Disk > ${DISK_THRESHOLD}%. Likely log/table growth (MySQL slow_log TABLE, mysql-data volume) - check the disk trend and prune if needed. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "disk_used_percent" \
        --dimensions Name=InstanceId,Value="$INSTANCE_ID" Name=path,Value="/" Name=fstype,Value="xfs" \
        --statistic Average \
        --period 300 \
        --threshold "$DISK_THRESHOLD" \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 4: High Network In — catches scraping / brute force against API
    log_info "Creating High Network In alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HighNetworkIn" \
        --alarm-description "Inbound > ${NET_IN_THRESHOLD}B over 5 min - possible scraping or brute-force against the API. Check Request Rate + HTTP Status Distribution. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "net_bytes_recv" \
        --dimensions Name=InstanceId,Value="$INSTANCE_ID" Name=interface,Value=ens5 \
        --statistic Sum \
        --period 300 \
        --threshold "$NET_IN_THRESHOLD" \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 5: High Network Out — catches data exfiltration / runaway API responses
    log_info "Creating High Network Out alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HighNetworkOut" \
        --alarm-description "Outbound > ${NET_OUT_THRESHOLD}B over 5 min - possible data exfiltration or runaway response sizes. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "net_bytes_sent" \
        --dimensions Name=InstanceId,Value="$INSTANCE_ID" Name=interface,Value=ens5 \
        --statistic Sum \
        --period 300 \
        --threshold "$NET_OUT_THRESHOLD" \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 6: High Disk IO Write — catches log flooding or runaway DB writes
    # name=nvme0n1 is the default EBS root device on t3.medium; update if the instance has more volumes
    log_info "Creating High Disk IO Write alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HighDiskIOWrite" \
        --alarm-description "Disk writes > ${DISKIO_WRITE_THRESHOLD}B over 5 min - possible log flooding or runaway DB writes. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "diskio_write_bytes" \
        --dimensions Name=InstanceId,Value="$INSTANCE_ID" Name=name,Value=nvme0n1 \
        --statistic Sum \
        --period 300 \
        --threshold "$DISKIO_WRITE_THRESHOLD" \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 7: HTTP 5xx Errors
    log_info "Creating HTTP 5xx alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HTTP5xx" \
        --alarm-description "More than ${HTTP5XX_THRESHOLD} HTTP 5xx in 5 min. Check Recent Backend Errors + Slowest Endpoints. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "HTTP5xxCount" \
        --statistic Sum \
        --period 300 \
        --threshold "$HTTP5XX_THRESHOLD" \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 8: Any Backend Error — fires immediately on a single ERROR event
    log_info "Creating backend error alarm (immediate)..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-BackendErrors" \
        --alarm-description "A backend ERROR was logged. See the Recent Backend Errors panel - logger_name pinpoints the source class. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "BackendErrorCount" \
        --statistic Sum \
        --period 60 \
        --threshold 0 \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --datapoints-to-alarm 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 9: Successive Backend Warnings — fires when WARNs appear in 3 of 5
    # consecutive 1-minute windows, avoiding noise from isolated warnings.
    log_info "Creating successive backend warnings alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-BackendWarnings" \
        --alarm-description "Backend WARNs in 3 of the last 5 minutes. Check the Recent Backend Errors panel. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "BackendWarnCount" \
        --statistic Sum \
        --period 60 \
        --threshold 0 \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 5 \
        --datapoints-to-alarm 3 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 10: Backend Silence — fires when server produces no logs for 5 minutes.
    # treat-missing-data=breaching means a CW Agent outage also triggers this alarm,
    # which is correct: both cases (app crash and agent failure) need attention.
    log_info "Creating backend silence alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-BackendSilence" \
        --alarm-description "No backend logs for 5 min - app crash or CW Agent failure (backend runs restart:'no', so a crash is a hard-down). Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "BackendLogCount" \
        --statistic Sum \
        --period 300 \
        --threshold 1 \
        --comparison-operator LessThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data breaching \
        --region "$AWS_REGION"

    # Alarm 11: MySQL Backup Silence — daily S3 dump must emit BackupSuccess once per 24h.
    # breaching = missing datapoint treated as alarm, so a failed/absent cron fires immediately.
    log_info "Creating MySQL backup silence alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-MysqlBackupSilence" \
        --alarm-description "No successful MySQL backup in 24h - the daily S3 dump cron failed or did not run. Check the /danteplanner/backup log group. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "BackupSuccess" \
        --dimensions Name=Job,Value=MysqlDump \
        --statistic Sum \
        --period 86400 \
        --threshold 1 \
        --comparison-operator LessThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data breaching \
        --region "$AWS_REGION"

    # Alarm 12: CloudWatch Log Export Silence — daily CW→S3 export heartbeat.
    log_info "Creating CloudWatch export silence alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-CwExportSilence" \
        --alarm-description "No successful CloudWatch to S3 log export in 24h - check the export cron (exec-bit fixed; verify IAM/S3 next run). Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "BackupSuccess" \
        --dimensions Name=Job,Value=CwLogExport \
        --statistic Sum \
        --period 86400 \
        --threshold 1 \
        --comparison-operator LessThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data breaching \
        --region "$AWS_REGION"

    # Alarm 13: HTTP 429 spike — bucket4j rate limiting may be blocking legitimate users.
    log_info "Creating HTTP 429 spike alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-HTTP429" \
        --alarm-description "More than 20 HTTP 429s in 5 min — bucket4j rate limiting may be blocking legitimate users (watch the SSE reconnect-on-refresh case). Check HTTP Status Distribution panel. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "HTTP429Count" \
        --statistic Sum \
        --period 300 \
        --threshold 20 \
        --comparison-operator GreaterThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data notBreaching \
        --region "$AWS_REGION"

    # Alarm 14: Traffic drop — fewer than 1 request in 5 min for 2 periods. This app has
    # 24/7 global traffic, so a sustained zero means an outage. Complements BackendSilence.
    log_info "Creating traffic-drop alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-TrafficDrop" \
        --alarm-description "Fewer than 1 request in 5 min for 2 consecutive periods — probable outage or upstream/DNS break. Complements BackendSilence. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "RequestCount" \
        --statistic Sum \
        --period 300 \
        --threshold 1 \
        --comparison-operator LessThanThreshold \
        --evaluation-periods 2 \
        --datapoints-to-alarm 2 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data breaching \
        --region "$AWS_REGION"

    # Alarm 15: Cloudflare IP Update Silence — daily SG allowlist refresh heartbeat.
    # breaching = a missing/failed run fires, matching the other cron-silence alarms.
    log_info "Creating Cloudflare IP update silence alarm..."
    aws cloudwatch put-metric-alarm \
        --alarm-name "DantePlanner-CloudflareIpSilence" \
        --alarm-description "No successful Cloudflare IP allowlist update in the last 24 hours — the security-group ingress rules may be stale, so origin access could break or over-expose. Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner" \
        --namespace "$NAMESPACE" \
        --metric-name "BackupSuccess" \
        --dimensions Name=Job,Value=CloudflareIpUpdate \
        --statistic Sum \
        --period 86400 \
        --threshold 1 \
        --comparison-operator LessThanThreshold \
        --evaluation-periods 1 \
        --alarm-actions "$TOPIC_ARN" \
        --ok-actions "$TOPIC_ARN" \
        --treat-missing-data breaching \
        --region "$AWS_REGION"

    # Alarms 16-17 (JVM heap > 90% of max, HikariCP pending sustained) land with the
    # backend Prometheus scrape — their metric names are fixed by the scrape's EMF config.
    # Added there so they aren't created in INSUFFICIENT_DATA against non-existent metrics.

    log_info "All alarms created successfully"
}
