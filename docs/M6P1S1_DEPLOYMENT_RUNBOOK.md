# M6P1S1 Deployment Runbook

## Scope

This runbook deploys the LOB Matrix Java service as a system-wide `systemd`
unit with automatic restart and boot startup. It also documents an optional
Chrony slew-mode configuration.

The runbook does not contain broker credentials. Store all secrets outside Git
in an environment file readable only by the service account.

## Prerequisites

- Ubuntu or another `systemd` Linux host
- Java 21
- Maven
- A checked-out LOB Matrix repository
- Sufficient writable disk capacity for raw WAL and Parquet data
- A non-root service account
- Chrony installed and running, or an explicitly chosen alternative NTP client

## Important host-wide warning

Chrony and `systemd-timesyncd` are host-wide clock services. Do not run both
as active time-discipline clients. On a multi-purpose host, inspect dependent
services before changing the time daemon.

The LOB engine uses monotonic timing for latency measurement. NTP provides
wall-clock discipline for storage and operational timestamps.

## Files

- `deploy/orderbook-engine.service.template`: parameterized systemd unit
- `deploy/orderbook-engine.env.example`: non-secret environment-file template
- `deploy/chrony/orderbook-engine-slew.conf.example`: optional Chrony drop-in
- `deploy/verify-systemd-service.sh`: read-only service verification helper

## Required substitutions

Replace the following template placeholders before installing the unit:

| Placeholder | Meaning |
|---|---|
| `{{SERVICE_USER}}` | Non-root Linux service account |
| `{{SERVICE_GROUP}}` | Service account group |
| `{{APP_DIR}}` | Repository or deployed application directory |
| `{{JAVA_HOME}}` | Java 21 home directory |
| `{{JAVA_BIN}}` | Java 21 executable |
| `{{APP_JAR}}` | Packaged Spring Boot JAR path |
| `{{ENV_FILE}}` | External owner-only environment-file path |
| `{{DATA_DIR}}` | Writable application data directory |
| `{{RUNTIME_DIR}}` | Writable transient/runtime directory |

## Install procedure

1. Build and test the application.

   ```bash
   mvn test
   mvn -DskipTests package
   ```

2. Create an external environment file with mode `0600`.

3. Substitute the deployment values into the service template.

4. Copy the resulting unit to `/etc/systemd/system/orderbook-engine.service`.

5. Run:

   ```bash
   sudo systemctl daemon-reload
   sudo systemctl enable --now orderbook-engine.service
   ```

6. Verify:

   ```bash
   sudo deploy/verify-systemd-service.sh
   ```

## Operational commands

```bash
sudo systemctl status orderbook-engine.service
sudo systemctl restart orderbook-engine.service
sudo systemctl stop orderbook-engine.service
sudo journalctl -u orderbook-engine.service -f
sudo journalctl -u orderbook-engine.service --since '1 hour ago'
```

## Restart verification

A service restart verifies startup and recovery without rebooting the host:

```bash
sudo systemctl restart orderbook-engine.service
sleep 10
sudo systemctl --no-pager status orderbook-engine.service
```

Check that:

- Status is `active (running)`.
- The service is `enabled`.
- The process is listening on the configured dashboard port.
- Logs contain no unhandled exception or restart loop.
- The dashboard reconnects successfully.

## Time synchronization verification

```bash
timedatectl status
chronyc tracking
chronyc sources -v
```

Expected conditions:

- `System clock synchronized: yes`
- Chrony is active
- Any competing NTP client is inactive
- `Leap status: Normal`
- Clock offset is stable and small

## Rollback

To stop only the LOB engine:

```bash
sudo systemctl disable --now orderbook-engine.service
```

To remove the unit:

```bash
sudo rm -f /etc/systemd/system/orderbook-engine.service
sudo systemctl daemon-reload
```

To revert from Chrony to `systemd-timesyncd`, only after a planned maintenance
decision:

```bash
sudo systemctl disable --now chrony.service
sudo systemctl unmask systemd-timesyncd.service
sudo systemctl enable --now systemd-timesyncd.service
timedatectl status
```

## Security notes

- Do not commit `.env` files containing credentials.
- Do not expose the dashboard publicly until authentication and transport
  controls are explicitly designed.
- Keep the engine under a non-root account.
- Review `systemd-analyze security orderbook-engine.service` after every
  unit change.
- Avoid `MemoryDenyWriteExecute=true` unless it has been validated with
  ONNX Runtime and other native/JNI libraries.
