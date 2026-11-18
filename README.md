# smarttraffic-zero-trust

> **Cómo ejecutarlo:** [GUIA_DE_USO.md](GUIA_DE_USO.md) (`vagrant up` levanta las 5 VMs) ·
> **Cómo explicarlo:** [docs/SUSTENTACION.md](docs/SUSTENTACION.md) ·
> **Pruebas:** `mvn test`
>
> Módulos: `smarttraffic-domain` (POO pura), `smarttraffic-security` (JWT hacia RabbitMQ),
> `auth-server`, `traffic-sensor-simulator`, `traffic-density-processor`, `traffic-archiver`,
> `user-dashboard-backend`, `alert-dispatcher`, `query-client` · Infra: `Vagrantfile`, `infra/`.

---

## 11. Ejercicio: Reto de Infraestructura: SmartTraffic - Zona Cero (Zero Trust) con Java, Spring Boot, POO y Persistencia

### Resumen del Proyecto
Este proyecto es una fusión del sistema de monitoreo SmartTraffic con un desafío de infraestructura de "confianza cero" (Zero Trust). El objetivo ya no es solo construir los microservicios, sino desplegarlos en un entorno de red hostil y realista compuesto por múltiples Máquinas Virtuales (VMs). El flujo de datos de SmartTraffic (Kafka para ingesta, RabbitMQ para distribución y consultas) se mantiene, pero cada componente (broker, microservicio, base de datos) será aislado en su propia VM o zona de seguridad. El reto es automatizar el despliegue, implementar autenticación de servicio a servicio (S2S) mediante tokens (JWT), bloquear toda la comunicación no autorizada usando reglas estrictas de nftables, e incorporar el ecosistema robusto de Java 17+ y Spring Boot con buenas prácticas de diseño orientado a objetos, persistencia relacional en PostgreSQL, pruebas unitarias automatizadas y control estricto de recursos por contenedor. Docker Compose ya no se usará como un único orquestador central que enlaza todo en un host plano; en su lugar, Docker y Compose se utilizarán para compilar, ejecutar y gobernar los servicios localmente dentro de cada una de las VMs aprovisionadas.

### Objetivos de Aprendizaje
- Diseño Híbrido: Justificar el uso de Kafka (log de eventos) y RabbitMQ (distribución de estado y mensajería asíncrona).
- Procesamiento de Flujos: Implementar el consumidor stateful de Kafka (KTable) y la lógica de replicación de estado en RabbitMQ.

- Infraestructura como Código (IaaC): Utilizar herramientas de aprovisionamiento (como Vagrant o Ansible) para definir y desplegar automáticamente la arquitectura de VMs.

- Red de Confianza Cero (Zero Trust): Implementar una política de red de "denegación por defecto" (default deny). Diseñar y aplicar reglas estrictas de nftables en cada VM, permitiendo únicamente el tráfico esencial entre IPs y puertos autorizados.

- Identidad y Autenticación de Servicios (S2S):
    - Crear un Servidor de Autoridad (Auth Server) que emita tokens JWT firmados.
    - Modificar los microservicios en Java Spring Boot para que soliciten tokens al Auth Server antes de operar.
    - Configurar Kafka (SASL/OAUTHBEARER) y RabbitMQ (rabbitmq_auth_backend_oauth2) para validar estos JWT en cada conexión, publicación y consumo, prohibiendo el acceso anónimo.

- Programación Orientada a Objetos (POO Avanzada) y Dominio Rico en Java (JDK 17+):
    - **Strategy:** Intercambiar algoritmos polimórficos de cálculo de densidad de tráfico sin alterar la lógica del consumidor.
    - **State:** Modelar las transiciones de estado de las zonas de tráfico (DESPEJADA, MODERADA, CONGESTIONADA) encapsulando reglas de transición sin anidar condicionales if-else o switch.
    - **Observer:** Emitir notificaciones de cambio crítico de congestión de forma desacoplada.
    - **Inversión de Dependencias (DIP):** Inyectar dependencias estrictamente por constructor en todos los componentes de Spring Boot, desacoplando el dominio de los clientes de mensajería y persistencia mediante interfaces de puerto.
        - Modelo de Dominio Rico vs. Anémico: Implementar entidades y agregados que encapsulen estado y comportamiento mediante métodos de negocio semánticos, eliminando getters y setters indiscriminados y protegiendo invariantes.
        - Inmutabilidad y Value Objects: Utilizar record de Java para DTOs, identificadores y contratos de mensajes, con validaciones compactas **Fail-Fast** en sus constructores ante valores negativos o identificadores nulos.
        - Principios SOLID y Patrones de Diseño:

- Estrategia de Pruebas Unitarias Aisladas (TDD con JUnit 5 y Mockito):
    - Diseñar pruebas de dominio puras en memoria RAM bajo el patrón AAA (Arrange, Act, Assert), ejecutadas en milisegundos sin arrancar Spring Boot, bases de datos ni sockets de red.
    - Validar la capa de orquestación y servicios mediante dobles de prueba (@Mock, @InjectMocks, verify) con Mockito para auditar interacciones y despacho de mensajes.

- Persistencia Relacional y Auditoría con PostgreSQL:
    - Integrar un motor relacional PostgreSQL para el almacenamiento persistente e inmutable del histórico de lecturas de sensores, auditoría de eventos y analítica.
    - Gestionar el mapeo objeto-relacional mediante Spring Data JPA / Hibernate respetando el encapsulamiento del modelo de dominio.

- Contenerización Multietapa y Gobernanza de Recursos (Docker en VMs):
    - Diseñar Dockerfiles multietapa con fase de compilación (Maven + OpenJDK con caché de dependencias) y fase de ejecución ligera (JRE Alpine con usuario del sistema no privilegiado appuser).
    - Definir límites de CPU (cpus) y memoria RAM (memory) por contenedor, sincronizándolos con las opciones de control de memoria ergonómica de la JVM (-XX:+UseContainerSupport, -XX:MaxRAMPercentage).
    - Configurar número de instancias y escalabilidad horizontal de consumidores mediante directivas de replicación (deploy.replicas).

### Arquitectura del Sistema (VMs y Zonas de Seguridad)

El sistema se distribuye en 5 Zonas de Seguridad (VMs) aprovisionadas de forma automatizada, donde los contenedores corren aislados y limitados en recursos de hardware:

- vm-auth (Zona de Autoridad):
    - Ejecuta el servicio auth_server (Microservicio 0) dentro de un contenedor Docker.
    - Es la única entidad facultada para firmar y emitir tokens JWT.

- vm-data (Zona de Datos y Persistencia):
    - Ejecuta los brokers de mensajería kafka (+ zookeeper) y rabbitmq.
    - Aloja el motor de base de datos relacional postgres-db en un contenedor con volúmenes persistentes gestionados (_named volumes_) para el histórico y auditoría del tráfico.
    - Representa el núcleo de persistencia y estado del sistema, con reglas de acceso restringidas.

- vm-ingest (Zona de Ingesta - Edge):
    - Ejecuta el microservicio en Java Spring Boot traffic_sensor_simulator.
    - Simula el borde de la red; posee cuotas reducidas de CPU y memoria.

- vm-core (Zona de Procesamiento y Persistencia):
    - Ejecuta traffic_density_processor (procesamiento stateful) y traffic_archiver (persistencia relacional a PostgreSQL) en contenedores Java Spring Boot.
    - Constituye el backend seguro encargado del cómputo algorítmico y archivo histórico.

- vm-app (Zona de Aplicación - DMZ):
    - Ejecuta user_dashboard_backend, alert_dispatcher y query_client (instanciable en múltiples réplicas).
    - Es la única zona que expone interfaces al exterior (el WebSocket del dashboard para el cliente web dashboard.html).

### Especificación Técnica de Microservicios y Componentes Java

Todos los microservicios backend se construyen sobre Java 17+ y Spring Boot, organizados con inyección de dependencias por constructor e interfaces desacopladas:

1. auth_server [en vm-auth]:
    - Expone el endpoint de autenticación /token.
    - Recibe credenciales de cliente (client_id, client_secret) y emite tokens JWT firmados con roles y permisos de acceso.

2. traffic_sensor_simulator [en vm-ingest] (Java / Spring Boot):
    - **Propósito:** Simular el envío periódico de lecturas de tráfico.
    - **Lógica POO:** Encapsula cada lectura en un Value Object inmutable (record) con validación **Fail-Fast** de rangos no negativos.
    - **Seguridad y Salida:** Solicita un JWT a vm-auth, autentica la conexión a Kafka en vm-data usando SASL/OAUTHBEARER y publica en raw_traffic_data.

3. traffic_density_processor [en vm-core] (Java / Spring Boot):
    - **Propósito:** Servicio stateful que calcula la densidad y el estado de la ciudad.
    - **Lógica POO:** Mantiene un agregado de dominio en memoria (ZoneTrafficAggregate), aplicando el patrón Strategy para calcular la densidad vehicular y el patrón State para la transición entre estados de congestión sin condicionales anidados.
    - **Seguridad y Enlaces:** Obtiene su JWT de vm-auth, consume de Kafka autenticado mediante SASL/OAUTHBEARER y publica el estado consolidado hacia RabbitMQ en vm-data autenticado con OAuth2.

4. traffic_archiver [en vm-core] (Java / Spring Boot):
    - **Propósito:** Guardar de forma duradera las lecturas crudas y eventos para auditoría y analítica.
    - **Persistencia (PostgreSQL):** Mapea los eventos a entidades JPA y los almacena en postgres-db mediante repositorios desacoplados (DIP).
    - **Seguridad y Entrada:** Consume de Kafka en vm-data presentando su JWT validado.

5. user_dashboard_backend [en vm-app] (Java / Spring Boot):
    - **Propósito:** Difundir actualizaciones de tráfico en tiempo real.
    - **Lógica POO:** Mapea mensajes AMQP hacia DTOs inmutables de presentación.
    - **Seguridad y Salida:** Autentica contra RabbitMQ con su JWT, consume del Fanout y emite el estado vía WebSocket.

6. alert_dispatcher [en vm-app] (Java / Spring Boot):
    - **Propósito:** Alertar anomalías de congestión y responder consultas asíncronas de rutas.
    - **Lógica POO:** Mantiene una réplica local del mapa de estados, utiliza el patrón Observer para gatillar alertas ante cambios de umbral y lanza excepciones de dominio controladas ante zonas inexistentes.
    - **Seguridad:** Presenta credenciales OAuth2 ante RabbitMQ para suscribirse al Fanout, consumir de query_traffic_queue y responder al Topic Exchange.

7. query_client [en vm-app] (Java / Spring Boot):
    - **Propósito:** Simular peticiones asíncronas de usuarios sobre el estado de un trayecto.
    - **Lógica e Instancias:** Configurable mediante múltiples réplicas para evidenciar el consumo concurrente. Publica consultas y procesa respuestas a través de una cola exclusiva con routing key en RabbitMQ previa autenticación con JWT.

8. dashboard.html [Frontend]:
    - Interfaz web que se conecta al WebSocket servido por user_dashboard_backend en vm-app.

### Suite de Pruebas Unitarias Aisladas (TDD con JUnit 5 y Mockito)
Las pruebas unitarias deben cumplir con aislamiento total de infraestructura:
- Pruebas de Dominio Puro (Sin Spring, sin BD, sin Brokers):
    - Ejecución directa en memoria RAM bajo la estructura AAA (Arrange, Act, Assert) en milisegundos.
    - Validación de invariantes de negocio en las entidades y agregados de tráfico.
    - Comprobación de fallos tempranos en los constructores compactos de los record ante datos erróneos o nulos.
    - Verificación de las transiciones de estado regidas por el patrón State.

- Pruebas de Capa de Servicios con Mockito:
    - Uso de @ExtendWith(MockitoExtension.class), @Mock e @InjectMocks para aislar los adaptadores de mensajería y repositorios.
    - Validación de comportamiento mediante verify(...), certificando que ante la llegada de una lectura se efectúe la persistencia en el repositorio y la publicación hacia el broker exactamente una vez sin levantar conexiones reales.

### Contenerización, Compilación Multietapa y Restricción de Recursos

Cada microservicio Java implementará un Dockerfile multietapa administrado localmente en su VM correspondiente mediante Compose:

- **Etapa** 1 (Compilación / Builder):
    - Uso de imagen base con Maven y OpenJDK (maven:3.9.6-eclipse-temurin-17-alpine).
    - Aislamiento del archivo pom.xml y descarga offline de dependencias (mvn dependency:go-offline -B) previa a la transferencia del código fuente para optimizar la caché de capas de Docker.
    - Compilación y empaquetado del archivo .jar excluyendo pruebas en la fase de empaquetado (mvn clean package -DskipTests).

- **Etapa 2** (Ejecución Ligera y Segura / Runtime):
    - Uso de imagen base mínima (eclipse-temurin:17-jre-alpine).
    - Creación y ejecución bajo un usuario del sistema no privilegiado (appuser:appgroup), respetando el principio de menor privilegio.
    - Copia exclusiva del archivo .jar compilado desde la etapa constructora.
    - Ajuste ergonómico de la JVM en contenedores mediante flags: JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError".

- **Gobernanza de Recursos** (CPU y RAM) e Instancias:
    - Definición explícita de directivas deploy.resources.limits fijando cuotas máximas de CPU (cpus) y memoria RAM (memory) para cada contenedor Spring Boot y para el motor postgres-db.
    - Configuración de instancias y escalamiento horizontal mediante deploy.replicas para servicios de procesamiento concurrente.

### Flujo de Desafío y Políticas de nftables (Zero Trust)
Cada VM aplicará una política por defecto de bloqueo absoluto (policy drop) tanto en la cadena de entrada (input) como en la de salida (output). Las reglas mínimas indispensables incluyen:

- vm-auth:
    - **input:** Permitir puerto de autenticación únicamente desde las IPs de vm-ingest, vm-core y vm-app.
    - **output:** Permitir retorno de paquetes establecidos/relacionados hacia dichas IPs.

- vm-data:
    - Permitir puerto 9092 (Kafka) únicamente desde vm-ingest y vm-core.
    - Permitir puerto 5672 (RabbitMQ) únicamente desde vm-core y vm-app.
    - Permitir puerto 5432 (PostgreSQL) únicamente desde la IP de vm-core (para el traffic_archiver).
        - **input:_
        - **output:** Permitir tráfico establecido/relacionado hacia los orígenes autorizados.

- vm-ingest:
    - Permitir puerto de Auth únicamente hacia vm-auth.
    - Permitir puerto 9092 únicamente hacia vm-data.
        - **output:**

- vm-core:
    - Permitir puerto de Auth hacia vm-auth.
    - Permitir puertos 9092 (Kafka), 5672 (RabbitMQ) y 5432 (PostgreSQL) hacia vm-data.
        - **output:**

- vm-app:
    - Permitir puerto de Auth hacia vm-auth.
    - Permitir puerto 5672 hacia vm-data.
        - **input:** Permitir puerto del WebSocket del dashboard hacia los clientes externos.
        - **output:**

### Flujo de Datos Detallado con Seguridad, Persistencia y Dominio Rico

1. **Arranque y Despliegue:**
    - Las VMs inician su entorno de red y aplican las tablas de nftables con política DROP.
    - Se inician los contenedores de base de datos PostgreSQL, Kafka, RabbitMQ y los servicios Java Spring Boot configurados con compilación multietapa y límites de CPU y memoria.

2. **Autenticación de Servicios (S2S):**
    - Cada microservicio Spring Boot invoca al auth_server en vm-auth a través del canal de red autorizado y obtiene su token JWT firmado.

3. **Fase 1: Ingesta, Validación y Persistencia:**
    - traffic_sensor_simulator genera lecturas con Value Objects (record), valida invariantes y se conecta a Kafka en vm-data usando SASL/OAUTHBEARER.
    - traffic_archiver (en vm-core), autenticado con su token, consume de Kafka y almacena transaccionalmente el registro en postgres-db a través de interfaces JPA desacopladas.

4. **Fase 2: Procesamiento Stateful y Distribución:**
    - traffic_density_processor (en vm-core) consume los eventos desde Kafka validando su token, actualiza su modelo de dominio rico en memoria (ZoneTrafficAggregate), ejecuta la estrategia de cálculo (Strategy) y actualiza el estado de congestión (State).
    - El procesador se autentica ante RabbitMQ en vm-data mediante OAuth2 y publica el estado consolidado de las zonas en el Exchange Fanout traffic_updates.

5. **Fase 3: Consumo de Estado y Replicación Local:**
    - alert_dispatcher y user_dashboard_backend en vm-app se autentican ante RabbitMQ y consumen desde sus colas vinculadas al Fanout.
    - alert_dispatcher actualiza su réplica en memoria y, mediante el patrón Observer, dispara notificaciones si se detectan umbrales de congestión críticos.
    - user_dashboard_backend transmite los estados consolidados por WebSocket hacia dashboard.html.

6. **Fase 4: Consultas Asíncronas Seguras:**
    - Las instancias de query_client (en vm-app), autenticadas con su JWT, envían consultas estructuradas a query_traffic_queue en RabbitMQ.
    - alert_dispatcher consume la solicitud, consulta su réplica de dominio local y publica el trayecto evaluado hacia el Topic Exchange query_answers usando la clave de enrutamiento del cliente, completando el ciclo de consulta asíncrona autenticada y aislada en red.

---

## Instrucciones de Entrega y Evaluación
Los ejercicios están diseñados como desafíos de arquitectura. Si bien el flujo del sistema está explicado paso a paso, su tarea principal es **analizar y diseñar** una solución distribuida robusta.

**El número de ejercicio corresponde al número del grupo.**

### Objetivo del Proyecto

El objetivo no es solo implementar, sino **justificar sus decisiones de diseño** aplicando tecnologías de nivel empresarial. Deberán integrar **Apache Kafka** y **RabbitMQ** en una misma arquitectura orquestada con Docker Compose.

### Lineamientos y Enfoque

- **Fuente de Datos:** Como hemos manejado en clase, la generación de datos será **aleatoria (random)**. 
    
- **Enfoque de la Materia:** El objetivo es dominar la implementación y la interoperabilidad de Kafka y RabbitMQ, independientemente del lenguaje de programación o la base de datos que se use en un entorno real.
    

### Entregables

1. **Repositorio de GitHub:** El enlace al repositorio de su proyecto.
    

### Presentación (La Evaluación)

- **Tiempo Límite:** Dispondrán de un máximo de **15 minutos** por equipo.
    
- **Formato:** Deberán presentar la aplicación funcionando y luego realizar una explicación concisa del código, la arquitectura y sus **decisiones de diseño.**

---

## INTEGRANTES
**Grupo 1 6A**
- [Daniel Perez](https://github.com/Whatfck)
- [Santiago Urbina](https://github.com/Santiago25-lab)