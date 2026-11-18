# =============================================================================
# Dockerfile multietapa de traffic-density-processor
#   Etapa 1 (builder): Maven + JDK 17, con cache de dependencias por capas.
#   Etapa 2 (runtime): solo JRE 17 Alpine + el .jar, con usuario NO privilegiado.
# Contexto de build: la raiz del repositorio (proyecto Maven multimodulo).
# =============================================================================

# ---------- Etapa 1: compilacion ----------
FROM maven:3.9.6-eclipse-temurin-17-alpine AS builder
WORKDIR /workspace

# 1) Solo los pom.xml: esta capa (y la descarga de dependencias) queda en cache
#    mientras no cambien las dependencias, aunque cambie el codigo fuente.
COPY pom.xml .
COPY smarttraffic-domain/pom.xml smarttraffic-domain/pom.xml
COPY smarttraffic-security/pom.xml smarttraffic-security/pom.xml
COPY auth-server/pom.xml auth-server/pom.xml
COPY traffic-sensor-simulator/pom.xml traffic-sensor-simulator/pom.xml
COPY traffic-density-processor/pom.xml traffic-density-processor/pom.xml
COPY traffic-archiver/pom.xml traffic-archiver/pom.xml
COPY user-dashboard-backend/pom.xml user-dashboard-backend/pom.xml
COPY alert-dispatcher/pom.xml alert-dispatcher/pom.xml
COPY query-client/pom.xml query-client/pom.xml
RUN mvn -B -q -pl traffic-density-processor -am dependency:go-offline

# 2) Codigo fuente: modulos compartidos + el propio servicio.
COPY smarttraffic-domain/src smarttraffic-domain/src
COPY smarttraffic-security/src smarttraffic-security/src
COPY traffic-density-processor/src traffic-density-processor/src
RUN mvn -B -q -pl traffic-density-processor -am clean package -DskipTests

# ---------- Etapa 2: ejecucion ligera y segura ----------
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup && mkdir -p /app/data && chown -R appuser:appgroup /app
WORKDIR /app
COPY --from=builder --chown=appuser:appgroup /workspace/traffic-density-processor/target/traffic-density-processor-*.jar app.jar
USER appuser:appgroup
# La JVM respeta los limites cpus/memory del contenedor (cgroups).
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
