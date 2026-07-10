# shellcheck shell=bash
# Human-readable completion summary.

# Print summary
print_summary() {
    echo ""
    echo "=========================================="
    echo "  CloudWatch Setup Complete"
    echo "=========================================="
    echo ""
    echo "Created Resources:"
    echo "  - SNS Topic: $SNS_TOPIC_NAME"
    echo "  - Alarms: HighCPU, LowMemory, HighDisk, HighNetworkIn, HighNetworkOut, HighDiskIOWrite, HTTP5xx, BackendErrors, BackendWarnings, BackendSilence, MysqlBackupSilence, CwExportSilence"
    echo "  - Metric Filters: HTTP5xxErrors (nginx), BackendErrors, BackendWarnings, BackendHeartbeat (backend)"
    echo "  - Dashboard: DantePlanner (System Resources widget + 10 alarms + log insights from /ecs/danteplanner/backend)"
    echo "  - Insights Queries (15): 9 backend, 4 nginx, 2 mysql"
    echo "  - S3 Logging: $S3_BACKUP_BUCKET -> $S3_LOGS_BUCKET"
    echo "  - IAM: CloudWatchAgentServerPolicy attached to instance role"
    echo "  - Log Retention: backend 90 days, nginx/mysql 30 days"
    echo ""
    echo "View in AWS Console:"
    echo "  Dashboard: https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#dashboards:name=DantePlanner"
    echo "  Alarms:    https://$AWS_REGION.console.aws.amazon.com/cloudwatch/home?region=$AWS_REGION#alarmsV2:"
    echo ""
    if [ -n "$ALERT_EMAIL" ]; then
        echo "IMPORTANT: Confirm email subscription in your inbox"
    fi
}
