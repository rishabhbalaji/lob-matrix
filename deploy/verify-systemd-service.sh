#!/usr/bin/env bash
set -euo pipefail

SERVICE_NAME="${1:-orderbook-engine.service}"

printf '%s\n' '=== SYSTEMD UNIT SYNTAX ==='
systemd-analyze verify "/etc/systemd/system/${SERVICE_NAME}"

printf '%s\n' '=== SERVICE STATUS ==='
systemctl --no-pager --full status "$SERVICE_NAME"

printf '%s\n' '=== ENABLEMENT ==='
systemctl is-enabled "$SERVICE_NAME"
systemctl is-active "$SERVICE_NAME"

printf '%s\n' '=== NTP STATUS ==='
timedatectl status
if command -v chronyc >/dev/null 2>&1; then
  printf '%s\n' '--- chronyc tracking ---'
  chronyc tracking
  printf '%s\n' '--- chronyc sources ---'
  chronyc sources -v
fi

printf '%s\n' '=== SERVICE SECURITY REVIEW ==='
systemd-analyze security "$SERVICE_NAME" || true

printf '%s\n' '=== RECENT SERVICE LOGS ==='
journalctl -u "$SERVICE_NAME" --no-pager -n 100
