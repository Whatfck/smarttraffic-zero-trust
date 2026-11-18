# Guía de uso y demostración: SmartTraffic Zero Trust

Todo se levanta desde **un solo PC** (Windows, Linux o Mac Intel) con **VirtualBox + Vagrant**.
Para la explicación de cada componente, ver [docs/SUSTENTACION.md](docs/SUSTENTACION.md).

## 1. Requisitos
- VirtualBox 7.x y Vagrant 2.4.x (en Windows: `winget install Oracle.VirtualBox` y `winget install Hashicorp.Vagrant`).
- 16 GB de RAM (las 5 VMs usan unos 8.7 GB) y unos 25 GB de disco libre.
- Internet **solo durante el primer `vagrant up`**: descarga la imagen Ubuntu, Docker, las imágenes base y las dependencias Maven.
- Opcional, para correr las pruebas en el host: JDK 17+ y Maven 3.9.

| VM | IP | Contenedores |
|---|---|---|
| vm-auth | 192.168.56.10 | auth-server (8081) |
| vm-data | 192.168.56.20 | zookeeper, kafka (9092), rabbitmq (5672), postgres-db (5432) |
| vm-ingest | 192.168.56.30 | traffic-sensor-simulator |
| vm-core | 192.168.56.40 | traffic-density-processor (x2), traffic-archiver |
| vm-app | 192.168.56.50 | user-dashboard-backend (8082), alert-dispatcher, query-client (x3) |

## 2. Levantar todo
Desde la raíz del repositorio:
```bash
vagrant up            # crea y aprovisiona las 5 VMs en orden (la primera vez tarda bastante)
vagrant status        # las 5 deben quedar "running"
```
Cada VM ejecuta `infra/scripts/provision.sh`:
1. Instala Docker y nftables.
2. Compila las imágenes multietapa y arranca los contenedores.
3. Aplica el firewall `default deny`.

Los secretos de cada servicio se generan solos la primera vez en `.secrets/smarttraffic.env`
(está en `.gitignore`).

Dashboard: **http://192.168.56.50:8082/dashboard.html**

Comandos útiles:
```bash
vagrant provision vm-core   # volver a compilar y desplegar una zona tras cambiar código
vagrant halt                # apagar todo (se conserva el estado)
vagrant up                  # volver a encender (el firewall y los contenedores arrancan solos)
vagrant destroy -f          # borrar las VMs
```

## 3. Guion de demostración (unos 10 minutos)

### 3.1 Contenedores, límites de recursos y réplicas
```bash
vagrant ssh vm-core -c "docker compose -p smarttraffic ps"
vagrant ssh vm-core -c "docker stats --no-stream"
vagrant ssh vm-app  -c "docker compose -p smarttraffic ps"            # 3 query-client
vagrant ssh vm-app  -c "docker exec smarttraffic-user-dashboard-backend-1 id"   # uid de appuser, no root
```

### 3.2 Firewall Zero Trust (nftables)
```bash
vagrant ssh vm-data -c "sudo nft list table inet smarttraffic"
# Permitido: vm-core -> PostgreSQL
vagrant ssh vm-core   -c "nc -zvw3 192.168.56.20 5432"
# Bloqueado: vm-ingest -> PostgreSQL (timeout: lo descarta la politica drop)
vagrant ssh vm-ingest -c "nc -zvw3 192.168.56.20 5432"
# Bloqueado: vm-app -> Kafka
vagrant ssh vm-app    -c "nc -zvw3 192.168.56.20 9092"
# Los descartes quedan registrados en el kernel
vagrant ssh vm-data   -c "sudo journalctl -k | grep ST-DROP | tail -5"
```

### 3.3 Identidad de servicios (JWT)
```bash
# Clave publica que usan Kafka y RabbitMQ para verificar tokens
vagrant ssh vm-core -c "curl -s http://192.168.56.10:8081/.well-known/jwks.json"
# Sin credenciales validas, la Autoridad no emite token
vagrant ssh vm-core -c "curl -s -o /dev/null -w '%{http_code}\n' -X POST -H 'Content-Type: application/json' -d '{\"clientId\":\"intruso\",\"clientSecret\":\"x\"}' http://192.168.56.10:8081/token"
# Tokens emitidos a cada servicio
vagrant ssh vm-auth -c "docker logs smarttraffic-auth-server-1 2>&1 | grep 'JWT issued' | tail -8"
```
Para mostrar que **no hay acceso anónimo**, pruebe RabbitMQ con usuario y contraseña. Se rechaza
porque el único backend es OAuth2:
```bash
vagrant ssh vm-data -c "docker logs smarttraffic-rabbitmq-1 2>&1 | grep -iE 'oauth|refused' | tail -5"
```

### 3.4 Flujo de datos
```bash
vagrant ssh vm-ingest -c "docker logs --tail 5 smarttraffic-traffic-sensor-simulator-1"
vagrant ssh vm-core   -c "docker compose -p smarttraffic logs --tail 10 traffic-density-processor"
vagrant ssh vm-app    -c "docker logs smarttraffic-alert-dispatcher-1 2>&1 | grep -E 'ALERTA|Answered' | tail -8"
vagrant ssh vm-app    -c "docker compose -p smarttraffic logs --tail 6 query-client"
```

### 3.5 Persistencia en PostgreSQL
```bash
vagrant ssh vm-data -c "docker exec smarttraffic-postgres-db-1 psql -U smarttraffic -c 'select zone_id, count(*) from traffic_readings group by zone_id;'"
vagrant ssh vm-data -c "docker exec smarttraffic-postgres-db-1 psql -U smarttraffic -c 'select event_type, count(*) from audit_events group by event_type;'"
```

### 3.6 Tolerancia a fallos (KTable)
```bash
vagrant ssh vm-core -c "docker stop smarttraffic-traffic-density-processor-1"
# la replica 2 toma todas las particiones; el dashboard sigue actualizandose
vagrant ssh vm-core -c "docker start smarttraffic-traffic-density-processor-1"
```

### 3.7 Cambiar la Strategy sin tocar código
Edite `DENSITY_STRATEGY` (`vehicle`, `speed` o `weighted`) en `infra/compose/docker-compose.vm-core.yml` y ejecute:
```bash
vagrant ssh vm-core -c "cd /opt/smarttraffic && sudo docker compose -p smarttraffic --env-file .secrets/smarttraffic.env -f infra/compose/docker-compose.vm-core.yml up -d"
```

## 4. Pruebas unitarias
```bash
mvn test
```
Son 48 pruebas: dominio puro en memoria, servicios con Mockito y la KTable con `TopologyTestDriver`.
Con JDK 21 o superior en el host, agregue `-Dnet.bytebuddy.experimental=true` si Mockito se queja.
Sin Maven en el host:
```bash
docker run --rm -v "$PWD":/app -w /app maven:3.9.6-eclipse-temurin-17-alpine mvn -q test
```

## 5. Problemas comunes
- **Una VM tarda o falla al compilar por red:** ejecute `vagrant provision <vm>`. El script es idempotente.
- **No abre el dashboard:** compruebe que `vm-app` esté arriba
  (`vagrant ssh vm-app -c "docker compose -p smarttraffic ps"`) y use la IP 192.168.56.50.
- **Kafka no acepta conexiones:** Kafka descarga el JWKS de vm-auth al arrancar, así que vm-auth
  debe estar arriba primero. Ejecute `vagrant ssh vm-data -c "docker restart smarttraffic-kafka-1"`.
