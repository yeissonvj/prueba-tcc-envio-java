# Versión Java / Spring Boot · qué se construyó y en qué difiere de .NET

La versión Java es una **traducción, no un rediseño** ([ADR-0012](adr/0012-nombres-en-espanol-y-diseno-portable.md)):
mismos contratos JSON, tópicos, grupos de consumo, encabezados, tablas y migraciones. Las dos versiones pueden
convivir en el mismo clúster durante una migración gradual.

## Stack

| | Versión |
|---|---|
| Java | 21 LTS (hilos virtuales) |
| Spring Boot | 4.1 (Spring Framework 7, Spring Security 7, Spring Kafka 4, Jackson 3) |
| Kafka | `kafka-clients` 4.2 |
| PostgreSQL | JDBC + `JdbcClient`, sin ORM |
| Pruebas | JUnit 6, AssertJ, Testcontainers 2, ArchUnit 1.5 |
| Imagen | `gcr.io/distroless/java21-debian12:nonroot` + agente OpenTelemetry 2.31 |

> El diseño original (Eje 2) proponía Spring Boot 3.x. Se usó **4.1** porque la línea 3.x salió del soporte
> libre en junio de 2026. Las diferencias visibles son los paquetes de Jackson 3 (`tools.jackson`) y los
> starters modulares (`spring-boot-starter-webmvc`, `spring-boot-starter-security-oauth2-resource-server`).

## Módulos

```
tcc-dominio               Java puro · 0 dependencias      Guia, EventoGuia, MaquinaEstados, PoliticaNotificacion
tcc-contratos             records V1 · 0 dependencias      EventoGuiaV1, EstadoGuiaCambiadoV1, GuiaV1...
tcc-aplicacion            → dominio + slf4j-api            casos de uso, puertos, decoradores
tcc-infraestructura       → aplicacion, contratos          Kafka, JDBC, Redis, proveedores, circuitos, telemetría
tcc-api                   Spring Web MVC + Security        ingesta, consulta, salud, relay de contingencia
tcc-procesador            Spring Kafka                     inbox + estado + outbox, relay de la bandeja
tcc-notificador           Spring Kafka                     escalera de reintentos, canal alterno, DLQ
tcc-arquitectura-pruebas  ArchUnit                         reglas de la hexagonal
```

## .NET → Java, pieza por pieza (implementado)

| Necesidad | .NET | Java | Dónde |
|---|---|---|---|
| Endpoints | Minimal APIs | `@RestController` sobre hilos virtuales | `tcc-api/.../endpoints` |
| Errores | Cadena de `IExceptionHandler` + ProblemDetails | `@RestControllerAdvice` + `ProblemDetail` (mismo cuerpo, `errors` por campo) | `ManejadorErrores`, `Problemas` |
| Validación | Validador propio | Validador propio (mismos mensajes) | `ValidadorEventoGuiaV1` |
| JWT | JwtBearer | `oauth2-resource-server` + `NimbusJwtDecoder` (solo RS256, emisor, audiencia, vigencia) | `ConfiguracionSeguridad` |
| Anti-suplantación | `AutorizadorOrigen` | `AutorizadorOrigen` (claim `azp`) | igual |
| Límite por cliente | RateLimiter token bucket | Bucket4j por `azp` → 429 + Retry-After | `LimitePorCliente` |
| Límite de cuerpo | Kestrel 64 KB | Filtro de 64 KB (Content-Length y chunked) → 413 | `LimiteCuerpo` |
| Circuito | Polly | Resilience4j (mismos umbrales) | `Circuitos` |
| Productor Kafka | Confluent.Kafka | `KafkaProducer` (acks=all, idempotente, lz4, `max.block.ms` acotado) | `ProductorKafka` |
| Consumo en paralelo | `ConsumidorKafka` + `DespachadorParticiones` | Contenedor de Spring Kafka: un hilo por partición, ack manual | `ConfiguracionProcesador` |
| Reintento con demora | Pausar particiones + poll | `Acknowledgment.nack(espera)`: pausa la partición sin bloquear el hilo | `ConsumidoresNotificador` |
| Cancelación al apagar | `CancellationToken` | `SenalApagado` (SmartLifecycle que se detiene antes que Kafka) | `SenalApagado` |
| PostgreSQL | Npgsql (batch) | `JdbcClient` + `TransactionTemplate` (mismo SQL) | `RepositorioGuiasJdbc`... |
| Redis | StackExchange.Redis (FailFast) | Lettuce vía Spring Data Redis (`REJECT_COMMANDS`) | `FiltroDuplicadosRedis` |
| Salud | HealthChecks `/salud/viva`, `/salud/lista` | Controlador propio, mismas rutas y textos (`Healthy`/`Degraded`/`Unhealthy`) | `SaludController` |
| Observabilidad | OpenTelemetry .NET | Agente OpenTelemetry (HTTP, JDBC, Kafka sin código) + API de métricas con los mismos nombres `tcc.*` | `Telemetria` |
| Traza a través del outbox | `Activity.Current.Id` en la fila | `traceparent` en la fila; el relay lo restaura antes de publicar | `RepositorioGuiasJdbc`, `RelayBandejaSalida` |
| Logs | JSON | JSON ECS con `trace_id` (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`) | `Dockerfile` |
| Documentación | Scalar | Swagger UI (springdoc), solo en el perfil `local` | `application-local.yml` |
| Imagen | chiseled | distroless `nonroot` | `Dockerfile` |

## Verificado

- `./mvnw verify`: **176 pruebas** en verde (unitarias, contrato JSON contra los mensajes de .NET, ArchUnit e
  integración con PostgreSQL 17 y Kafka 4.1 reales).
- Sistema completo en Docker con las imágenes Java: `/salud/lista` → `Healthy` y **`infra/pruebas/humo.sh` (el mismo
  de .NET, sin cambios) superada**.
- Latencia "Kafka recibió → estado visible": **p95 = 0,28 s** en local (30 eventos, entorno sin carga).
- Métricas `tcc_*` y `http_server_request_duration_seconds` en Prometheus con los mismos nombres: el tablero de
  Grafana y las 6 alertas funcionan sin cambios.
- Trazas de punta a punta en Jaeger: **API → Kafka → procesador → outbox → notificador en una sola traza**.

## Diferencias conscientes con la versión .NET

| Tema | .NET | Java | Por qué |
|---|---|---|---|
| Vaciado de la contingencia | Envíos en lote (se inician todos y se esperan juntos) | Uno por uno, en orden | Más simple y el orden es evidente; la contingencia es un camino excepcional. Si tras una caída larga el vaciado fuera lento, se paraleliza por guía. |
| Prueba de contrato | Instantánea de OpenAPI | `ContratoJsonPruebas` contra los JSON que produce .NET | Lo que no puede romperse al migrar es el mensaje en Kafka, no el documento OpenAPI. |
| Configuración | `Seccion__Clave` | Variables de entorno de Spring (`KAFKA_SERVIDORES`, `SPRING_DATASOURCE_URL`...) y perfil `local` | Convención de cada plataforma. |
| Esperas de reintento | `00:01:00` | `1m` (Duration de Spring) | Convención de cada plataforma; el panel de pruebas ya envía el formato nuevo. |

## Limitaciones conocidas

- **Carga y caos:** los resultados de `docs/pruebas.md` son de la versión .NET (`pruebas-carga/resultados/dotnet/`).
  Los scripts de k6, la reconciliación y el panel sirven sin cambios; falta correrlos contra Java.
- **Sin filtro de trazas para `/salud`:** .NET no trazaba las sondas; con el agente se trazan (ruido menor en Jaeger).
