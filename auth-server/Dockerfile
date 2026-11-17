FROM maven:3.9.9-eclipse-temurin-17-alpine AS builder
WORKDIR /workspace
COPY pom.xml .
COPY smarttraffic-domain/pom.xml smarttraffic-domain/pom.xml
COPY traffic-sensor-simulator/pom.xml traffic-sensor-simulator/pom.xml
COPY smarttraffic-processor/pom.xml smarttraffic-processor/pom.xml
COPY traffic-archiver/pom.xml traffic-archiver/pom.xml
COPY app-gateway/pom.xml app-gateway/pom.xml
COPY auth-server/pom.xml auth-server/pom.xml
RUN mvn -pl auth-server dependency:go-offline -B
COPY auth-server/src auth-server/src
RUN mvn -pl auth-server clean package -DskipTests -B

FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
WORKDIR /app
COPY --from=builder /workspace/auth-server/target/auth-server-*.jar app.jar
USER appuser
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
