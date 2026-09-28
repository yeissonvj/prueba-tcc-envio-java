# syntax=docker/dockerfile:1
# Una sola receta para los tres servicios:
#   docker build --build-arg MODULO=tcc-api         -t tcc-api .
#   docker build --build-arg MODULO=tcc-procesador  -t tcc-procesador .
#   docker build --build-arg MODULO=tcc-notificador -t tcc-notificador .

# ---------- Compilación: JDK y Maven completos, nunca llegan a producción ----------
FROM maven:3.9-eclipse-temurin-21 AS compilacion
ARG MODULO
ARG OTEL_AGENTE=2.31.1
WORKDIR /src

# El repositorio local de Maven va en un caché de BuildKit: las dependencias se descargan una vez
# y se comparten entre las tres imágenes (locked: las compilaciones paralelas no lo corrompen).
COPY pom.xml ./
COPY tcc-dominio/ tcc-dominio/
COPY tcc-contratos/ tcc-contratos/
COPY tcc-aplicacion/ tcc-aplicacion/
COPY tcc-infraestructura/ tcc-infraestructura/
COPY tcc-api/ tcc-api/
COPY tcc-procesador/ tcc-procesador/
COPY tcc-notificador/ tcc-notificador/
COPY tcc-arquitectura-pruebas/pom.xml tcc-arquitectura-pruebas/
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q package -DskipTests -pl ${MODULO} -am \
 && cp ${MODULO}/target/servicio-exec.jar /servicio.jar

# Agente de OpenTelemetry: trazas HTTP, JDBC y Kafka sin código; exporta también las métricas tcc_* y los logs.
# Se descarga con Maven para verificar su checksum.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn -B -q dependency:copy -Dartifact=io.opentelemetry.javaagent:opentelemetry-javaagent:${OTEL_AGENTE} \
        -DoutputDirectory=/otel -Dmdep.stripVersion=true

# ---------- Ejecución: distroless (sin shell ni gestor de paquetes), usuario no root ----------
FROM gcr.io/distroless/java21-debian12:nonroot AS final
WORKDIR /app
COPY --from=compilacion /servicio.jar /app/servicio.jar
COPY --from=compilacion /otel/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar
ENV JAVA_TOOL_OPTIONS="-javaagent:/app/opentelemetry-javaagent.jar -XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError" \
    OTEL_EXPORTER_OTLP_PROTOCOL=grpc \
    OTEL_METRIC_EXPORT_INTERVAL=10000 \
    LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs
EXPOSE 8080
USER nonroot
ENTRYPOINT ["java", "-jar", "/app/servicio.jar"]
