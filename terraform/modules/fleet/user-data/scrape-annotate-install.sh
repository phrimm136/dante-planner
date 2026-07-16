# Install the scrape-annotation converger: k3s re-applies its bundled CoreDNS
# manifest on every server start, reverting the pod-template scrape annotations,
# so a k3s-start-triggered oneshot re-converges them. Consumed by cp.sh.tftpl
# (new instances) and the State Manager association (live instances) — idempotent.
cat >/usr/local/bin/scrape-annotate.sh <<'EOF'
#!/bin/sh
set -u
KUBECTL="/usr/local/bin/k3s kubectl"
until $KUBECTL -n kube-system get deployment coredns >/dev/null 2>&1; do sleep 10; done
sleep 60
until $KUBECTL -n argocd patch statefulset argocd-application-controller --type merge -p '{"spec":{"template":{"metadata":{"annotations":{"prometheus.io/scrape":"true","prometheus.io/port":"8082","prometheus.io/path":"/metrics","prometheus.io/job":"argocd"}}}}}'; do sleep 10; done
until $KUBECTL -n external-secrets patch deployment external-secrets --type merge -p '{"spec":{"template":{"metadata":{"annotations":{"prometheus.io/scrape":"true","prometheus.io/port":"8080","prometheus.io/path":"/metrics","prometheus.io/job":"external-secrets"}}}}}'; do sleep 10; done
until $KUBECTL -n kube-system patch deployment coredns --type merge -p '{"spec":{"template":{"metadata":{"annotations":{"prometheus.io/scrape":"true","prometheus.io/port":"9153","prometheus.io/path":"/metrics","prometheus.io/job":"coredns"}}}}}'; do sleep 10; done
EOF
chmod +x /usr/local/bin/scrape-annotate.sh
cat >/etc/systemd/system/scrape-annotate.service <<'EOF'
[Unit]
Description=Re-apply Prometheus scrape annotations after k3s start
After=k3s.service

[Service]
Type=oneshot
TimeoutStartSec=600
ExecStart=/usr/local/bin/scrape-annotate.sh

[Install]
WantedBy=k3s.service
EOF
systemctl daemon-reload
systemctl enable scrape-annotate.service
