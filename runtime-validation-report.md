# Runtime Validation Report

**Generated:** 2026-11-15  
**Target:** SmartTraffic Zero Trust

## Summary

| Step | Status | Exit code | Evidence |
|---|---:|---:|---|
| Maven reactor build | PASS | 0 | All six modules compiled; 3 domain tests passed |
| Compose configuration | PASS | 0 | `docker compose config --quiet` |
| Startup | PASS | 0 | Auth, Kafka, PostgreSQL, RabbitMQ, sensor, archiver, processor and gateway started |
| Kafka ingestion | PASS | 0 | `raw_traffic_data` consumed by processor and archiver |
| PostgreSQL persistence | PASS | 0 | 123 rows in `traffic_readings` during validation |
| RabbitMQ distribution | PASS | 0 | `dashboard_updates` queue with 2 consumers and no pending messages |
| Gateway query | PASS | 0 | PUT returned 202; subsequent GET returned the same snapshot |
| JWT issuance | PASS | 0 | `/token` returned HTTP 200 and a 15-minute JWT |

## Environment

- Docker daemon: available.
- Node.js: available (`v26.8.1`); browser E2E was not required for this backend-focused validation.
- Maven: executed through the project Maven container because it is not installed on the host.
- ARM host: Java images are built with `linux/amd64` emulation because the selected Maven image has no ARM64 manifest.

## Known gaps

1. Kafka and RabbitMQ currently use local-demo transport authentication/configuration.
   The production requirement for Kafka `SASL/OAUTHBEARER` and RabbitMQ
   `rabbitmq_auth_backend_oauth2` still needs broker plugin configuration and
   JWKS/public-key validation.
2. The app gateway contains the dashboard WebSocket, local state replica, alert
   dispatcher and query API in one deployable module. They are not yet three
   independently deployable VM services.
3. Vagrant/Ansible provision the five zones and firewall rules, but Compose is
   still run from the project checkout rather than being split into one Compose
   deployment per VM.
4. The dashboard file uses a simple API demonstration; a full STOMP WebSocket
   browser flow still needs an end-to-end browser test.

## Verdict

**NEEDS_SIGNOFF** — the core event/persistence/distribution path is working and
validated, but the OAuth broker integration and per-VM service separation remain
required before claiming complete compliance with every line of the workshop.
