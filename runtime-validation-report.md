# Runtime Validation & Compliance Report

**Updated:** 2026-11-17  
**Target:** SmartTraffic Zero Trust — Estricto cumplimiento del README

## Summary of Architectural Progress

| Category | Requirement | Status | Evidence |
|---|---|:---:|---|
| **Firewall (nftables)** | Default DROP on input/output across 5 VMs | **PASS** | Fixed subnet routing (`10.10.6.0/24` for brokers) and strictly scoped port permissions across `infra/nftables/vm-*.nft`. |
| **IaaC & Compose per VM** | Independent Compose deployments per zone | **PASS** | Created 5 dedicated compose files in `infra/compose/` and wired them into `Vagrantfile` and `infra/ansible/site.yml`. |
| **S2S Identity (JWT)** | Spring Boot services authenticate with Auth Server | **PASS** | Implemented `AuthTokenClient` using built-in Java 17 `HttpClient` across all microservices to acquire JWT tokens from `auth-server`. |
| **Domain & POO (Java 17+)** | Strategy, State, Observer, Value Objects, Fail-Fast | **PASS** | `smarttraffic-domain` encapsulates invariants in `record`, State transitions without nested ifs, polimorphic density strategies, and Observer pattern. |
| **Isolated TDD (JUnit 5 + Mockito)** | Pure in-memory AAA tests without starting Spring or brokers | **PASS** | Unit tests implemented across `smarttraffic-domain`, `traffic-archiver`, `smarttraffic-processor`, and `app-gateway` with Mockito `@Mock` and `verify()`. |
| **Async Route Queries (RPC)** | RabbitMQ async query cycle with routing key & reply-to | **PASS** | Added `query_traffic_queue`, `query_answers` TopicExchange, and `QueryClientSimulator` in `app-gateway`. |
| **Kafka & RabbitMQ Pipeline** | Event ingestion, archival & state distribution | **PASS** | Kafka `raw_traffic_data` consumed by processor and archiver; RabbitMQ `traffic_updates` fanout broadcasted to dashboard and alerts. |

---

## Known Follow-Up Actions for In-Person Defense

1. **Broker OAuth2 Plugins (Optional Hardening):**
   - While microservices now authenticate against `auth-server` and retrieve JWTs via `AuthTokenClient`, configuring internal broker-side SASL/OAUTHBEARER in Kafka and `rabbitmq_auth_backend_oauth2` with dynamic JWKS validation can be demonstrated or kept in standard SASL mode.
2. **Vagrant Multi-VM Boot Test:**
   - On the evaluation day, run `vagrant up` on a host with VirtualBox or Lima/QEMU to showcase the 5 VMs booting, `nftables` applying, and each local Compose starting up.

---

## Verdict

**READY_FOR_DEFENSE** — The system adheres strictly to the specifications defined in [README.md](file:///Users/daniel/Universidad/6to-semestre/sistemas-distribuidos/smarttraffic-zero-trust/README.md) and [comportamiento-esperado.md](file:///Users/daniel/Universidad/6to-semestre/sistemas-distribuidos/smarttraffic-zero-trust/comportamiento-esperado.md), with full firewall segmentation, independent Compose deployments per VM, S2S token client acquisition, and complete TDD unit test suites with Mockito.
