# Matriz de cumplimiento del enunciado

Cada requisito del enunciado (README) con **dónde está implementado** y **cómo se comprobó en
las 5 VMs**. Leyenda: ✅ cumplido y verificado en ejecución · 🧪 cumplido y cubierto por
pruebas unitarias · 📄 cumplido por configuración o documentación.

Verificación en vivo realizada el 18/11/2026 sobre las 5 VMs, después de un `vagrant halt` +
`vagrant up` completo.

## 1. Objetivos de aprendizaje

| Requisito | Estado | Implementación / evidencia |
|---|:---:|---|
| Diseño híbrido Kafka (log) + RabbitMQ (estado y mensajería) justificado | ✅ | `docs/SUSTENTACION.md` §1. Kafka: `raw_traffic_data` (3 particiones). RabbitMQ: fanout `traffic_updates`, cola `query_traffic_queue`, topic `query_answers` |
| Consumidor stateful de Kafka (**KTable**) | ✅ 🧪 | `traffic-density-processor/.../TrafficTopology.java` (`groupByKey().aggregate()` → KTable con store `zone-traffic-state` + changelog). Probado con `TrafficTopologyTest` (TopologyTestDriver). En vivo, 2 réplicas se reparten las zonas |
| Replicación de estado en RabbitMQ | ✅ | Fanout `traffic_updates` → una cola exclusiva por instancia (`dashboard.<id>`, `alert_dispatcher.<id>`); `TrafficStateReplica` en alert-dispatcher |
| IaC con Vagrant o Ansible | ✅ | `Vagrantfile` + `infra/scripts/provision.sh`. Alternativa `infra/ansible/site.yml` (mismo script) |
| nftables con default deny y solo tráfico esencial | ✅ | `infra/nftables/vm-*.nft`: `policy drop` en input, forward y output, con IPs exactas. En vivo: vm-core→5432 permitido; vm-ingest→5432, vm-app→9092 y vm-ingest→5672 bloqueados; descartes registrados con `ST-DROP` |
| Auth Server que emite JWT firmados | ✅ 🧪 | `auth-server`: `POST /token` (client_credentials, JSON o formulario). RS256 + `kid`, `/.well-known/jwks.json`. `TokenIssuerTest`. En vivo: token válido para el sensor y `401` para un cliente desconocido |
| Microservicios piden token antes de operar | ✅ | Kafka: `OAuthBearerLoginCallbackHandler` pide el token antes de conectar. RabbitMQ: `smarttraffic-security/RabbitOAuth2AutoConfiguration` (`OAuth2ClientCredentialsGrantCredentialsProvider`). Log del auth-server: `JWT issued to client '...'` |
| Kafka SASL/OAUTHBEARER validando JWT, sin acceso anónimo | ✅ | `infra/compose/docker-compose.vm-data.yml`: listener EXTERNAL `SASL_PLAINTEXT` + `OAuthBearerValidatorCallbackHandler`, que valida firma (JWKS), emisor y audiencia `kafka`. `connections.max.reauth.ms=300000`. El listener interno PLAINTEXT solo escucha en 127.0.0.1 |
| RabbitMQ `rabbitmq_auth_backend_oauth2` en conexión, publicación y consumo | ✅ | `infra/rabbitmq/20-smarttraffic.conf`: único backend OAuth2, sin usuarios internos. Clave pública de vm-auth fijada (`auth_oauth2.signing_keys`). Permisos por scope, incluida la routing key del topic. En vivo: las conexiones aparecen con el `sub` del JWT |
| **Strategy** de densidad intercambiable | ✅ 🧪 | `TrafficDensityStrategy` + `Vehicle`/`Speed`/`WeightedDensityStrategy`; se selecciona con `DENSITY_STRATEGY` (`DensityStrategyConfiguration`). `strategyIsInterchangeable()` |
| **State** sin if-else/switch anidados | ✅ 🧪 | `TrafficZoneState`, `Clear/Moderate/CongestedZoneState`, `TransitionRule`, `StateCatalog` (mapa en lugar de switch). `TrafficZoneStateTest` cubre las 10 transiciones con histéresis |
| **Observer** de cambios críticos desacoplado | ✅ 🧪 | `CongestionObserver` + `CongestionChangedEvent`; sujetos `ZoneTrafficAggregate` y `TrafficStateReplica`; `CongestionAlertNotifier`. En vivo: `ALERTA DE CONGESTION: zona ...` |
| DIP: inyección estricta por constructor e interfaces de puerto | ✅ | Puertos: `TrafficStatePublisher`, `ReadingArchive`, `AuditTrail`, `TrafficReadingPublisher`, `DashboardBroadcaster`, `RouteAnswerPublisher`, `RouteQuerySender`, `ClientRegistry`, `SigningKeyProvider`. No hay `@Autowired` en campos |
| Modelo de dominio rico (sin setters indiscriminados, invariantes) | ✅ 🧪 | `ZoneTrafficAggregate`: sin setters; `record()` protege la zona, normaliza la densidad y el contador solo crece. `rejectsReadingsFromAnotherZone()` |
| `record` para DTOs, identificadores y contratos, con Fail-Fast | ✅ 🧪 | Contratos: `TrafficReading`, `ZoneTrafficSnapshot`, `RouteQuery`, `RouteEvaluation`, `CongestionChangedEvent`. Identificadores: `ZoneId`, `ClientIdentity`. DTO: `ZoneStatusView`. Value Objects: `TrafficDensity`, `RegisteredClient`. `ValueObjectsTest` |
| Pruebas de dominio puras AAA, sin Spring/BD/red | 🧪 | `smarttraffic-domain/src/test` (4 clases, 26 casos), se ejecutan en milisegundos |
| Pruebas de servicios con Mockito (`@Mock`, `@InjectMocks`, `verify`) | 🧪 | `TrafficProcessingServiceTest`, `ArchivingServiceTest` (persistencia exactamente una vez), `TrafficSensorSimulatorTest` (publicación exactamente una vez), `RouteQueryServiceTest`, `DashboardServiceTest`, `RouteQueryClientTest`, `TokenIssuerTest`. **Total: 49 pruebas, todas pasan** (`mvn test`) |
| PostgreSQL: histórico inmutable, auditoría y analítica | ✅ | `traffic-archiver`: `TrafficReadingEntity` y `AuditEventEntity` con `@Immutable` y `updatable=false`, escritas en la misma transacción. En vivo: miles de lecturas y eventos `READING_ARCHIVED`. Consultas analíticas de ejemplo en `GUIA_DE_USO.md` §3.5 |
| JPA respetando el encapsulamiento | ✅ | Entidades sin setters, se crean con `from(...)` o `of(...)`; constructor protegido solo para JPA; los repositorios solo los usan los adaptadores |
| Dockerfile multietapa (Maven + caché de dependencias / JRE Alpine + appuser) | ✅ | `*/Dockerfile`: `maven:3.9.6-eclipse-temurin-17-alpine`; copia de `pom.xml` + `mvn dependency:go-offline -B` antes del código; `mvn clean package -DskipTests`; `eclipse-temurin:17-jre-alpine`; `appuser:appgroup`; solo se copia el `.jar`; `JAVA_OPTS` exacto. En vivo: `docker inspect` muestra `user=appuser:appgroup` |
| Límites de CPU/RAM por contenedor sincronizados con la JVM | ✅ | `deploy.resources.limits` (`cpus` y `memory`) en todos los servicios Spring y en `postgres-db`. JVM: `-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError` |
| Réplicas / escalado horizontal (`deploy.replicas`) | ✅ | `traffic-density-processor: replicas: 2` y `query-client: replicas: 3`. En vivo: los 5 contenedores corriendo y las particiones repartidas |

## 2. Arquitectura (5 zonas)

| Zona | Exigido | Estado | Implementado |
|---|---|:---:|---|
| vm-auth | auth_server en contenedor, única entidad que firma | ✅ | `docker-compose.vm-auth.yml`; la clave privada persiste solo en el volumen de vm-auth |
| vm-data | kafka (+zookeeper), rabbitmq y postgres-db con named volumes | ✅ | `docker-compose.vm-data.yml`; volúmenes `kafka-data`, `zookeeper-*`, `rabbitmq-data`, `postgres-data` |
| vm-ingest | traffic_sensor_simulator con cuota reducida | ✅ | 0.35 CPU / 300 MB |
| vm-core | traffic_density_processor y traffic_archiver | ✅ | `docker-compose.vm-core.yml` |
| vm-app (DMZ) | user_dashboard_backend, alert_dispatcher, query_client (réplicas); única zona expuesta | ✅ | `docker-compose.vm-app.yml`; solo el puerto 8082 está abierto al exterior |
| Compose dentro de cada VM, no un orquestador central | — | ✅ | Un compose por VM en `infra/compose/`; se eliminó el compose plano |

## 3. Microservicios

| # | Servicio | Estado | Evidencia en vivo |
|---|---|:---:|---|
| 1 | auth_server `/token` con roles y permisos | ✅ | Emite `scope` y `roles` por identidad |
| 2 | traffic_sensor_simulator (record fail-fast, JWT, OAUTHBEARER, `raw_traffic_data`) | ✅ | `Reading published zone=...`; offsets crecientes en Kafka |
| 3 | traffic_density_processor (agregado en memoria, Strategy, State, JWT, OAuth2 a RabbitMQ) | ✅ | `Zone 'center' changed MODERADA -> CONGESTIONADA (density=0.76)` |
| 4 | traffic_archiver (JPA, repositorios desacoplados, JWT) | ✅ | Tabla `traffic_readings` creciendo y auditoría |
| 5 | user_dashboard_backend (AMQP → DTO inmutable → WebSocket) | ✅ | `GET /api/zones` y dashboard conectado por WebSocket |
| 6 | alert_dispatcher (réplica local, Observer, excepción de dominio, topic) | ✅ | `ALERTA DE CONGESTION ...`, `Answered query ... OK`; zona inexistente responde `ZONE_NOT_FOUND` |
| 7 | query_client (réplicas, cola exclusiva con routing key) | ✅ | 3 réplicas: `<- answer ... status=OK latency=21ms` |
| 8 | dashboard.html | ✅ | `http://192.168.56.50:8082/dashboard.html`, con las 5 zonas en vivo |

## 4. Reglas nftables pedidas

| VM | Regla del enunciado | Estado |
|---|---|:---:|
| vm-auth | input: puerto de auth solo desde ingest, core y app; output: established | ✅ (además: vm-data, para la clave pública) |
| vm-data | 9092 desde ingest/core; 5672 desde core/app; 5432 solo desde core; output established | ✅ (además: salida a vm-auth:8081 para la clave) |
| vm-ingest | output: auth y 9092 | ✅ |
| vm-core | output: auth, 9092, 5672, 5432 | ✅ |
| vm-app | output: auth y 5672; input: puerto del dashboard | ✅ |

Las únicas reglas añadidas (vm-data ↔ vm-auth:8081) son las que necesitan los brokers para
obtener la clave pública con la que validan los JWT; el enunciado lista "reglas mínimas".

## 5. Decisiones a explicar si preguntan
- **`network_mode: host`:** sin él, el NAT de Docker hace que el tráfico pase por `forward` y esquive las reglas de input de nftables.
- **Key pinning en RabbitMQ:** RabbitMQ 4 solo acepta descargar un JWKS por https; por eso se fija la clave pública en el broker.
- **Repositorio Maven semilla (`.mvn-repo`):** es una optimización para redes lentas. `go-offline` sigue ejecutándose antes de copiar el código, como pide el enunciado.
- **Listener interno de Kafka sin SASL:** solo para el propio broker, ligado a 127.0.0.1 y bloqueado por el firewall.
