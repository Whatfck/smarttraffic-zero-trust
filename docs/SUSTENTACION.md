# Guía de sustentación: SmartTraffic, Zona Cero (Zero Trust)

Este documento explica **qué hace cada pieza, por qué se hizo así y dónde está en el código**.
Está organizado por materia, para responder las preguntas de cada profesor.

---

## 0. La idea en 30 segundos

Sensores simulados publican lecturas de tráfico en **Kafka**. Un procesador *stateful*
(**Kafka Streams / KTable**) calcula la densidad y el estado de cada zona, y difunde el
resultado por **RabbitMQ** (Fanout). En la zona de aplicación, un dashboard lo muestra en vivo
por **WebSocket**, un despachador genera **alertas** y responde **consultas asíncronas**. Un
archivador guarda todo en **PostgreSQL**.

Cada componente vive en su **propia VM**. Ninguna VM confía en otra:
- **Red:** nftables con política `drop` por defecto. Solo pasan las conexiones IP:puerto autorizadas.
- **Identidad:** cada servicio pide un **JWT firmado** al auth_server, y Kafka y RabbitMQ
  rechazan cualquier conexión sin un token válido.

```
                 ┌──────────── vm-auth 192.168.56.10 ────────────┐
                 │ auth_server :8081  (/token, /.well-known/jwks) │
                 └───────▲──────────▲──────────▲─────────▲───────┘
                  JWT    │          │          │         │ JWKS (clave pública)
┌─ vm-ingest .30 ─┐      │   ┌─ vm-core .40 ───┴──────┐  │   ┌─ vm-app .50 (DMZ) ──────────────┐
│ sensor_simulator├──────┘   │ density_processor (x2) │  │   │ user_dashboard_backend :8082 ◄──┼── navegador
└───────┬─────────┘          │ traffic_archiver       │  │   │ alert_dispatcher                │
        │ 9092 (OAUTHBEARER) └──┬──────────┬──────┬───┘  │   │ query_client (x3)               │
        ▼                       │9092      │5672  │5432  │   └──────────────┬──────────────────┘
┌──────────────────────── vm-data 192.168.56.20 ─────────┴────────────────┐ │ 5672 (OAuth2)
│ Kafka(+ZooKeeper) :9092   RabbitMQ :5672   PostgreSQL :5432            ◄─┘
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 1. Sistemas Distribuidos

### ¿Por qué Kafka **y** RabbitMQ? (diseño híbrido)
| | Kafka | RabbitMQ |
|---|---|---|
| Modelo | **Log de eventos** persistente y particionado | **Broker de mensajes** con enrutamiento |
| Para qué lo usamos | Ingesta de lecturas crudas (`raw_traffic_data`) | Difundir el estado y hacer consultas petición/respuesta |
| Por qué | Re-lectura (replay), orden por partición, varios consumidores independientes (processor y archiver leen lo mismo con distinto *group id*), base para procesamiento con estado | Exchanges **Fanout** (difusión a todos) y **Topic** (respuesta dirigida por routing key), colas exclusivas por cliente y baja latencia |

### Flujo completo
1. `traffic-sensor-simulator` publica `TrafficReading` en `raw_traffic_data` con **clave = zoneId**.
   Así, todas las lecturas de una zona caen en la misma partición y en orden.
2. `traffic-archiver` (consumer group `traffic-archiver`) guarda cada lectura en PostgreSQL.
3. `traffic-density-processor` (aplicación Kafka Streams `traffic-density-processor`):
   `KStream → groupByKey → aggregate → KTable<zoneId, ZoneTrafficSnapshot>`.
   Cada cambio de la KTable se publica en el **Fanout `traffic_updates`**.
4. `user-dashboard-backend` y `alert-dispatcher` tienen **cada uno su propia cola exclusiva**
   enlazada al Fanout. Como el Fanout copia el mensaje a todas las colas enlazadas, todos reciben
   todo: eso es **replicación de estado**.
5. `query-client` (3 réplicas) envía `RouteQuery` a `query_traffic_queue`. `alert-dispatcher` la
   resuelve con su réplica local y responde al **Topic `query_answers`** con la routing key
   `query.<idCliente>`, que solo coincide con la cola de ese cliente.

### KTable: ¿qué es y por qué es *stateful*?
- La KTable es la **vista materializada del último valor por clave** (el estado actual de cada zona).
- Vive en un *state store* (`zone-traffic-state`) respaldado por un **topic changelog** en Kafka.
  Si el contenedor muere, al volver **reconstruye el estado** leyendo ese changelog.
- Con `deploy.replicas: 2`, Kafka **reparte las particiones** entre las dos instancias y cada una
  es dueña del estado de sus zonas: así se escala horizontalmente.
- Código: `traffic-density-processor/.../TrafficTopology.java`.

### Consistencia y concurrencia
- **Orden:** garantizado por partición (clave = zoneId).
- **Réplicas eventualmente consistentes:** las de vm-app descartan fotos más viejas que la actual
  (`TrafficStateReplica.apply` compara `updatedAt`).
- **Consumidores competidores vs. difusión:** `query_traffic_queue` es una cola compartida, así
  que las consultas se reparten (con `concurrency = 2`). Las colas del Fanout son una por
  instancia, así que todas reciben todo.

### Autenticación servicio a servicio (S2S)
1. Cada microservicio tiene su identidad (`client_id`, `client_secret`) y pide un token a
   `POST /token` con `grant_type=client_credentials`.
2. El **auth_server** valida las credenciales y firma un **JWT RS256** con estos claims:
   `iss`, `sub` (el servicio), `aud` (kafka y/o rabbitmq), `scope` (permisos), `roles`, `exp` (5 min).
3. **Kafka (SASL/OAUTHBEARER):** el cliente presenta el JWT al conectar. El broker verifica la firma
   con el **JWKS** de vm-auth y también el emisor y la audiencia `kafka`. Además
   `connections.max.reauth.ms` obliga a presentar un token vigente cada 5 minutos.
4. **RabbitMQ (`rabbitmq_auth_backend_oauth2`):** **no existen usuarios ni contraseñas**. El JWT es
   la credencial y sus *scopes* deciden qué puede hacer cada servicio:
   `rabbitmq.write:smarttraffic/traffic_updates`, por ejemplo, permite publicar en ese exchange y
   nada más. En el Topic `query_answers` también se autoriza la **routing key**: el
   alert_dispatcher solo publica con `query.*` y cada query_client solo lee `query.qc-*`.
   - **Clave fijada (key pinning):** RabbitMQ solo acepta descargar un JWKS por https. Por eso,
     al aprovisionar vm-data se descarga la clave pública de vm-auth (`/.well-known/signing-key.pem`)
     y se **fija** en el broker (`auth_oauth2.signing_keys.<kid>`). Así verifica firmas sin depender
     de la red y nadie puede suplantar el servidor de claves.
   - En la consola de RabbitMQ, las conexiones aparecen con el usuario **`sub` del token**
     (por ejemplo `traffic-density-processor`): RabbitMQ no tiene ningún usuario configurado.
5. **¿Por qué RS256 y JWKS?** La clave **privada** nunca sale de vm-auth. Los brokers solo tienen la
   **pública**, así que pueden verificar tokens pero no fabricarlos.

Archivos: `auth-server/.../TokenIssuer.java`, `JwksController.java`,
`auth-server/src/main/resources/application.yml` (permisos de cada identidad),
`infra/compose/docker-compose.vm-data.yml`, `infra/rabbitmq/20-smarttraffic.conf`,
`smarttraffic-security/.../RabbitOAuth2AutoConfiguration.java`.

---

## 2. Sistemas Operativos

### Infraestructura como Código
- `Vagrantfile`: define las 5 VMs (Ubuntu 22.04, VirtualBox), con su IP, RAM y CPU, y les monta el
  repositorio en `/opt/smarttraffic`.
- `infra/scripts/provision.sh`, en cada VM:
  1. Retira el firewall de la zona, porque instalar y compilar necesita Internet.
  2. Instala Docker Engine, Compose y nftables.
  3. Compila y arranca los contenedores de la zona.
  4. Aplica el firewall `default deny` y lo deja persistente en `/etc/nftables.conf`.
- `infra/ansible/site.yml` es la alternativa con Ansible para máquinas ya existentes.

### nftables y Zero Trust
- La tabla `inet smarttraffic` tiene las cadenas `input`, `forward` y `output`, todas con `policy drop`.
- Solo se permite:
  - el loopback;
  - las respuestas de conexiones ya autorizadas (`ct state established,related`);
  - SSH de gestión desde el hipervisor (`10.0.2.2`);
  - los flujos IP:puerto exactos del enunciado.
- Lo que se descarta queda registrado (`log prefix "ST-DROP-IN: "`) y contado (`counter drop`).
- **¿Por qué `network_mode: host`?** Con la red puente de Docker, el tráfico a los contenedores
  pasa por NAT y por la cadena `forward`, así que **esquiva** las reglas de `input` del host. Con
  `host`, los contenedores usan la pila de red de la VM y el firewall los filtra directamente.
- **¿Por qué no `flush ruleset`?** Borraría las tablas que gestiona Docker. Usamos una tabla propia
  que se recarga de forma idempotente.

| VM | Entra (input) | Sale (output, conexiones nuevas) |
|---|---|---|
| vm-auth | 8081 desde ingest, core, app y data (JWKS) | nada |
| vm-data | 9092 desde ingest y core · 5672 desde core y app · 5432 desde core | 8081 hacia auth (JWKS) |
| vm-ingest | nada | 8081 auth · 9092 data |
| vm-core | nada | 8081 auth · 9092, 5672, 5432 data |
| vm-app | 8082 desde cualquiera (clientes web) | 8081 auth · 5672 data |

### Contenedores, recursos y la JVM
- **Dockerfile multietapa:**
  - *Builder* (`maven:3.9.6-eclipse-temurin-17-alpine`): primero copia solo los `pom.xml` y corre
    `mvn dependency:go-offline`. Esa capa queda en caché y no se repite si solo cambia el código.
  - *Runtime* (`eclipse-temurin:17-jre-alpine`): solo el `.jar`, ejecutado como **`appuser`** sin
    privilegios (principio de menor privilegio).
- **Límites en Compose:** `deploy.resources.limits.cpus` y `memory`. Docker los aplica con
  **cgroups** del kernel.
- **JVM:**
  - `-XX:+UseContainerSupport` hace que la JVM lea el límite del cgroup y no la RAM de la VM.
  - `-XX:MaxRAMPercentage=75` deja el 25 % para la memoria fuera del heap (metaspace, hilos, buffers).
  - `-XX:+ExitOnOutOfMemoryError`: si se queda sin memoria, el proceso muere y Docker lo reinicia
    (`restart: unless-stopped`), en vez de quedar "zombi".
- **Escalado:** `deploy.replicas: 2` en el processor y `3` en query-client.

---

## 3. Programación Orientada a Objetos

Todo el dominio está en `smarttraffic-domain` y es **Java puro**: no depende de Spring, Kafka ni JPA.

| Concepto | Dónde | Cómo explicarlo |
|---|---|---|
| **Strategy** | `TrafficDensityStrategy` + `VehicleDensityStrategy`, `SpeedDensityStrategy`, `WeightedDensityStrategy` | El agregado llama a `strategy.calculate(reading)` sin saber cuál es. Se elige con la variable `DENSITY_STRATEGY` (`DensityStrategyConfiguration`), sin tocar el consumidor (Open/Closed). |
| **State** | `TrafficZoneState`, `ClearZoneState`, `ModerateZoneState`, `CongestedZoneState`, `TransitionRule` | Cada estado declara **sus** reglas de transición, y el agregado solo hace `state = state.next(density)`. No hay `if-else` ni `switch` sobre el estado. Incluye **histéresis**: se sube a CONGESTIONADA con 0.70, pero solo se baja con menos de 0.55. |
| **Observer** | `CongestionObserver`, `CongestionChangedEvent`; sujetos `ZoneTrafficAggregate` y `TrafficStateReplica` | Quien emite no conoce a quien escucha. En vm-app, `CongestionAlertNotifier` alerta al **cruzar** el umbral crítico. |
| **DIP / puertos** | `TrafficStatePublisher`, `ReadingArchive`, `AuditTrail`, `DashboardBroadcaster`, `RouteAnswerPublisher`, `RouteQuerySender`, `TrafficReadingPublisher`, `ClientRegistry`, `SigningKeyProvider` | Los servicios dependen de interfaces. Los adaptadores (RabbitMQ, Kafka, JPA, WebSocket) las implementan. Todo se inyecta **por constructor**, sin `@Autowired` en campos. |
| **Modelo rico** | `ZoneTrafficAggregate` | Sin setters: el estado solo cambia con el método de negocio `record(reading)`, que protege las invariantes (lectura de la misma zona, densidad normalizada, contador creciente). |
| **Value Objects (`record`)** | `TrafficReading`, `TrafficDensity`, `ZoneTrafficSnapshot`, `RouteQuery`, `RouteEvaluation`, `RegisteredClient`, `ZoneStatusView` | Son inmutables y validan en el constructor compacto (fail-fast): con datos inválidos no se puede crear el objeto. |
| **Excepción de dominio** | `ZoneNotFoundException` | El alert_dispatcher la captura y responde `ZONE_NOT_FOUND`, sin caerse. |
| **Persistencia sin romper el encapsulamiento** | `TrafficReadingEntity`, `AuditEventEntity` | Son `@Immutable`, sin setters, y se crean con `from(reading)`. El constructor vacío protegido existe solo porque JPA lo exige. |
| **SOLID** | S: una responsabilidad por clase · O: nuevas Strategy o State sin modificar las existentes · L: cualquier estrategia o estado sustituye a otro · I: puertos pequeños · D: puertos e inyección | |

### Pruebas (JUnit 5 + Mockito)
- **Dominio puro:** se ejecuta en milisegundos, sin Spring, BD ni red, con patrón **AAA**:
  `ZoneTrafficAggregateTest`, `TrafficZoneStateTest` (la tabla completa de transiciones),
  `ValueObjectsTest` (fail-fast) y `TrafficStateReplicaTest`.
- **Servicios con Mockito:** `@ExtendWith(MockitoExtension.class)`, `@Mock`, `@InjectMocks` y
  `verify(..., times(1))`. Por ejemplo, `ArchivingServiceTest` comprueba que la lectura se
  persiste **exactamente una vez**, y `TrafficProcessingServiceTest` que se publica una vez.
- **KTable sin broker:** `TrafficTopologyTest` usa `TopologyTestDriver`, que es Kafka Streams en memoria.
- Para ejecutarlas: `mvn test` (48 pruebas).

---

## 4. Preguntas probables y respuestas cortas

- **¿Qué pasa si alguien sin token se conecta a Kafka?** El handshake SASL falla y el broker
  cierra la conexión. No hay listener anónimo expuesto: el `INTERNAL` solo escucha en 127.0.0.1.
- **¿Y si roban un token?** Expira en 5 minutos, solo sirve para la audiencia y los scopes de ese
  servicio, y además el firewall exige la IP correcta.
- **¿Qué pasa si se cae el processor?** La otra réplica toma sus particiones (rebalanceo) y
  reconstruye el estado desde el changelog.
- **¿Por qué el dashboard tiene una cola exclusiva por instancia?** Si dos instancias compartieran
  la cola, cada una recibiría solo la mitad de los mensajes (consumidores competidores).
- **¿Dónde está la persistencia inmutable?** Las tablas `traffic_readings` y `audit_events` son
  append-only (`@Immutable`, columnas `updatable = false`).
- **¿Por qué ZooKeeper?** El enunciado lo pide (`kafka (+ zookeeper)`), y escucha solo en loopback.
