# Guía de Uso y Demostración: SmartTraffic Zero Trust

Esta guía detalla paso a paso cómo compilar, ejecutar, probar y sustentar el sistema **SmartTraffic Zero Trust**. Está diseñada tanto para el desarrollo diario como para servir de guion durante la **evaluación presencial de 15 minutos**.

---

## 1. Modos de Ejecución

El sistema soporta dos modalidades de despliegue:

| Modo | Caso de Uso | Requisitos |
| :--- | :--- | :--- |
| **Modo A: Multi-VM Distribuido (Vagrant)** | **Evaluación oficial / Rúbrica estricta** con 5 VMs aisladas y cortafuegos `nftables`. | Vagrant + VirtualBox |
| **Modo B: Monolito Local (Docker Compose)** | Desarrollo rápido, pruebas de código y depuración sin levantar 5 máquinas virtuales. | Docker + Docker Compose |

---

## 2. Preparación y Compilación de Pruebas Unitarias (TDD)

Antes de levantar los contenedores, valida que todas las pruebas unitarias puras en memoria (AAA con JUnit 5 y Mockito) pasen al 100%:

### Si tienes Maven en el host:
```bash
mvn clean test
```

### Si no tienes Maven instalado en el host (vía Docker):
```bash
docker run --rm -v "$(pwd)":/app -w /app maven:3.9.6-eclipse-temurin-17-alpine mvn test
```

> [!NOTE]
> Estas pruebas se ejecutan en RAM en menos de 2 segundos sin abrir sockets, sin levantar bases de datos ni arrancar Spring Boot, certificando el aislamiento exigido en el README.

---

## 3. Despliegue en Modo A: Multi-VM Oficial (Vagrant + nftables)

Este es el modo exigido por la rúbrica del proyecto.

### Paso 1: Iniciar las 5 Máquinas Virtuales
En la raíz del proyecto, ejecuta:
```bash
vagrant up
```
Vagrant aprovisionará en orden:
1. `vm-auth` (`10.10.2.10`): Instala `nftables`, aplica reglas y arranca `auth-server`.
2. `vm-data` (`10.10.6.10`): Arranca Kafka, RabbitMQ y PostgreSQL.
3. `vm-ingest` (`10.10.3.10`): Arranca el simulador de sensores.
4. `vm-core` (`10.10.4.10`): Arranca el procesador de densidad y el archivador JPA.
5. `vm-app` (`10.10.5.10`): Arranca el gateway con WebSocket y el despachador de alertas.

### Paso 2: Verificar el estado de las VMs
```bash
vagrant status
```

### Paso 3: Probar la Seguridad Zero Trust (`nftables`)
Para demostrar en la sustentación que la política `DROP` por defecto está activa y bloqueando tráfico no autorizado:

#### A. Tráfico Autorizado (Debe conectar):
```bash
# vm-core puede conectar a PostgreSQL en vm-data (5432)
vagrant ssh vm-core -c "nc -zvw3 10.10.6.10 5432"
# Salida esperada: Connection to 10.10.6.10 5432 port [tcp/*] succeeded!
```

#### B. Tráfico Bloqueado / Hostil (Debe ser descartado silenciosamente por DROP):
```bash
# vm-ingest INTENTA conectar a PostgreSQL en vm-data (NO AUTORIZADO)
vagrant ssh vm-ingest -c "nc -zvw3 10.10.6.10 5432"
# Salida esperada: Timeout / Connection timed out (Descartado por nftables)
```

#### C. Inspeccionar las reglas del kernel:
```bash
vagrant ssh vm-data -c "sudo nft list ruleset"
```

---

## 4. Despliegue en Modo B: Desarrollo Rápido Local (Docker Compose)

Si estás probando cambios locales sin máquinas virtuales:

```bash
# 1. Levantar toda la arquitectura localmente
docker compose up -d

# 2. Verificar que todos los servicios estén 'Up'
docker compose ps

# 3. Ver logs en tiempo real del flujo de datos
docker compose logs -f traffic-sensor traffic-processor app-gateway
```

---

## 5. Verificación de Componentes y Flujo de Datos

### 1. Autenticación de Servicios (S2S Token Exchange)
Prueba manual de solicitud de JWT al Servidor de Autoridad:
```bash
curl -X POST http://localhost:8081/token \
  -H "Content-Type: application/json" \
  -d '{"clientId":"smarttraffic","clientSecret":"change-me-in-production"}'
```
*Respuesta esperada:*
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsIn...",
  "expiresIn": 900
}
```

### 2. Persistencia en Base de Datos (PostgreSQL)
Verifica que las lecturas crudas se estén archivando de forma inmutable:
```bash
docker exec -it $(docker ps -qf "name=postgres") psql -U smarttraffic -d smarttraffic -c \
  "SELECT id, zone_id, vehicles, average_speed, observed_at FROM traffic_readings ORDER BY id DESC LIMIT 5;"
```

### 3. Consultas Asíncronas RPC (RabbitMQ)
Observa en los logs de `app-gateway` cómo el cliente de consultas (`QueryClientSimulator`) envía peticiones a `query_traffic_queue` y recibe respuestas correlacionadas desde `query_answers`:
```bash
docker compose logs -f app-gateway | grep -E "QueryClient|Answered async query"
```

### 4. Consola de Administración de RabbitMQ
* **URL:** `http://localhost:15672` (o `http://10.10.6.10:15672` en Modo A)
* **Usuario:** `smarttraffic`
* **Contraseña:** `change-me-in-production`
* Podrás ver el Exchange Fanout `traffic_updates`, las colas `dashboard_updates`, `query_traffic_queue` y el Topic Exchange `query_answers`.

---

## 6. Visualización Frontend en Tiempo Real (Dashboard)

1. Abre el archivo [dashboard.html](file:///Users/daniel/Universidad/6to-semestre/sistemas-distribuidos/smarttraffic-zero-trust/dashboard.html) directamente en cualquier navegador web:
   * En macOS por terminal:
     ```bash
     open dashboard.html
     ```
2. **Qué observar en la pantalla:**
   * **Badge superior:** Cambiará a verde `"WebSocket Conectado (:8082)"` tan pronto se enlace con `user_dashboard_backend`.
   * **Tarjetas por zona:** Muestran el identificador de la zona (`north`, `center`, `south`), la barra de porcentaje de saturación y el badge de estado:
     * 🟢 `DESPEJADA` (< 35% de densidad).
     * 🟡 `MODERADA` (35% a 70% de densidad).
     * 🔴 `CONGESTIONADA` (≥ 70% de densidad).
   * **Alerta Crítica:** Si una zona supera el umbral del 70%, el patrón *Observer* dispara la notificación y aparecerá un banner rojo de alerta crítica en el dashboard.
   * **Consola de eventos:** Muestra el stream JSON en vivo de los mensajes recibidos desde RabbitMQ.

---

## 7. Guion Sugerido para la Sustentación (15 Minutos)

| Minuto | Tema | Qué mostrar / Qué decir |
| :---: | :--- | :--- |
| **00 - 03** | **Arquitectura y Justificación** | • Explicar por qué Kafka para ingesta de alto throughput en el edge y RabbitMQ para distribución Fanout/RPC.<br>• Mostrar el diagrama de 5 zonas de red en [comportamiento-esperado.md](file:///Users/daniel/Universidad/6to-semestre/sistemas-distribuidos/smarttraffic-zero-trust/comportamiento-esperado.md). |
| **03 - 06** | **Demostración Zero Trust (nftables)** | • Abrir terminal en `vm-core` y mostrar que conecta a Postgres.<br>• Abrir terminal en `vm-ingest` y mostrar el *timeout* al intentar llegar a Postgres (DROP en acción).<br>• Mostrar el archivo `vm-data.nft`. |
| **06 - 09** | **Flujo End-to-End y Frontend en Vivo** | • Abrir [dashboard.html](file:///Users/daniel/Universidad/6to-semestre/sistemas-distribuidos/smarttraffic-zero-trust/dashboard.html) con el WebSocket conectado.<br>• Ver las transiciones reactivas de color y la generación de alertas críticas en vivo. |
| **09 - 12** | **POO Avanzada, Dominio y TDD** | • Mostrar en código el agregador `ZoneTrafficAggregate` (patrones *State*, *Strategy* y *Observer* sin condicionales anidados).<br>• Mostrar los Value Objects con `record` y validación *Fail-Fast*.<br>• Ejecutar `mvn test` demostrando la ejecución en milisegundos con Mockito. |
| **12 - 14** | **Persistencia y Consultas RPC** | • Hacer `SELECT` en PostgreSQL mostrando las lecturas guardadas.<br>• Mostrar los logs del ciclo asíncrono RPC de `QueryClientSimulator` sobre RabbitMQ. |
| **14 - 15** | **Conclusiones y Preguntas** | Responder preguntas del docente o evaluador. |

---

## 8. Comandos Útiles para Limpieza y Reinicio

```bash
# Detener contenedores locales
docker compose down

# Detener contenedores y borrar volúmenes (reinicia la BD limpia)
docker compose down -v

# Suspender o apagar las VMs de Vagrant
vagrant halt

# Destruir completamente las VMs para volver a crearlas desde cero
vagrant destroy -f
```
