# TCC · Plataforma de eventos de guías — Java / Spring Boot

Sistema de alta concurrencia que **captura** eventos de estado de guías desde varios sistemas, **actualiza el estado** casi en tiempo real y **notifica** al cliente, **sin perder eventos y sin degradar la operación** cuando algo falla.

Prueba técnica para *Desarrollador Advance* · implementación en **Java 21 + Spring Boot 4.1**, traducción de la [versión .NET 10](https://github.com/yeissonvj/prueba-tcc-envio) con los mismos contratos, tópicos, migraciones e infraestructura. Las dos versiones pueden convivir en el mismo clúster.

## Cómo revisar esta prueba en 10 minutos

| # | Qué | Dónde |
|---|---|---|
| 1 | **Presentación** de los 3 ejes (el Eje 2 técnico describe este diseño Java) | [https://yeissonvj.github.io/prueba-tcc-envio/presentacion/](https://yeissonvj.github.io/prueba-tcc-envio/presentacion/) |
| 2 | **Resultados** medidos de la versión Java | [Resultados en una mirada](#resultados-en-una-mirada) (abajo) |
| 3 | **De .NET a Java**: equivalencias pieza por pieza, qué se verificó y en qué difiere | [docs/java.md](docs/java.md) |
| 4 | **Arquitectura y decisiones**: componentes, garantías y 12 ADR | [docs/arquitectura.md](docs/arquitectura.md) · [docs/adr](docs/adr/README.md) |
| 5 | **Código**: hexagonal en 7 módulos Maven + ArchUnit | [tcc-dominio](tcc-dominio) · [tcc-aplicacion](tcc-aplicacion) · [tcc-infraestructura](tcc-infraestructura) · [tcc-api](tcc-api) |
| 6 | **Pruebas**: 176 automatizadas, integración con Testcontainers | [docs/pruebas.md](docs/pruebas.md) |

**Construido y verificado:** la solución completa en Java (API, procesador, notificador), probada con PostgreSQL y Kafka reales y con el sistema completo en Docker.
**Reutilizado de .NET sin cambios:** migraciones SQL, Keycloak, observabilidad (Collector, Prometheus, Grafana, Jaeger, Loki), prueba de humo, scripts de carga k6 y panel de pruebas.

```text
  TMS / Transporte (token OAuth2)
            |
            | POST /api/v1/eventos-guia  ->  202 Accepted
            v
  +--------------------+   Kafka caído   +------------------------+
  |  tcc-api           | --------------> | contingencia           |
  |  valida, autoriza  |                 | (PostgreSQL)           |
  +--------------------+                 +------------------------+
            | acks=all                               | relay (cuando Kafka vuelve)
            v                                        |
  +--------------------------------+                 |
  | Kafka: guias.eventos.recibidos | <---------------+
  +--------------------------------+
            |
            v
  +--------------------+          +----------------------------------+
  | tcc-procesador     | -------> | PostgreSQL                       |
  | inbox/estado/outbox|  1 tx    | guias, historial, bandeja_salida | <--- GET /api/v1/guias (API)
  +--------------------+          +----------------------------------+
                                                   | relay
                                                   v
                                  +--------------------------------+
                                  | Kafka: guias.estados.cambiados |
                                  +--------------------------------+
                                                   |
                                                   v
  +--------------------------+           +--------------------+
  | reintentos 1m / 10m / 1h | <-------> | tcc-notificador    | ---> SMS / correo al cliente
  |                          |           | SMS -> correo      |
  +--------------------------+           +--------------------+
```

## Resultados en una mirada

| | |
|---|---|
| **Exactamente un efecto** | 50 procesamientos concurrentes del mismo evento → 1 fila (PostgreSQL real); un cambio repetido → 1 sola notificación |
| **Orden** | 10 guías con 5 estados intercalados, procesadas en 3 particiones en paralelo → las 50 transiciones aplicadas en orden, ninguna tardía |
| **Resiliencia** | Kafka caído → contingencia; Kafka y base caídos → 503; SMS caído → escalera de reintentos → correo; poison pill → DLQ sin frenar la partición |
| **Compatibilidad con .NET** | Mensajes de Kafka idénticos (`ContratoJsonPruebas`); la prueba de humo de .NET pasa sin cambios contra Java |
| **Latencia** | Kafka → estado visible: **p95 0,28 s** en local sin carga (SLO: p95 < 5 s) |
| **Observabilidad** | Mismas métricas `tcc_*` (tablero y 6 alertas sin cambios); **una sola traza API → procesador → notificador** en Jaeger, también a través del outbox |
| **Calidad** | 176 pruebas (unitarias, contrato JSON, ArchUnit e integración con Testcontainers), compilación con avisos como errores |

## Cómo ejecutarlo

**Requisitos:** Docker Desktop (WSL 2) · JDK 21 (solo para desarrollo). Maven no hace falta: el proyecto trae `mvnw`.

### Con un doble clic (Windows)
`iniciar-local.bat` levanta Docker Desktop si hace falta, compila las imágenes, levanta todo el entorno (Kafka ×3, PostgreSQL, Redis, Keycloak, observabilidad y aplicaciones) y el panel de pruebas, y abre el navegador. Si la versión .NET estaba arriba, la detiene primero (usan los mismos puertos). `detener-local.bat` lo apaga conservando los datos.

### Todo el sistema en contenedores
```bash
docker compose -f infra/docker-compose.yml --profile aplicaciones up -d --build
API=http://localhost:8090 ./infra/pruebas/humo.sh
```
La prueba de humo pide un token a Keycloak, envía un evento (202), lo repite (200), envía uno inválido (400) y verifica que el estado aparece por la consulta.

### Desarrollo (IntelliJ / Maven)
```bash
docker compose -f infra/docker-compose.yml up -d      # solo infraestructura
./mvnw install -DskipTests                            # una vez: instala los módulos compartidos
./mvnw -pl tcc-api spring-boot:run -Dspring-boot.run.profiles=local
./mvnw -pl tcc-procesador spring-boot:run -Dspring-boot.run.profiles=local
./mvnw -pl tcc-notificador spring-boot:run -Dspring-boot.run.profiles=local
```
En Windows, `mvnw.cmd`. En IntelliJ: abrir la carpeta y ejecutar `AplicacionApi`, `AplicacionProcesador` y `AplicacionNotificador` con el perfil `local`. Documentación interactiva: http://localhost:5013/swagger-ui.html

### Pruebas
```bash
./mvnw verify                                         # 176 pruebas, incluye Testcontainers (Docker encendido)
```
Carga y reconciliación: ver [`docs/pruebas.md`](docs/pruebas.md).

**Pruebas manuales con botones** (carga, brokers caídos, PostgreSQL, Redis, SMS, kill del procesador) contra el entorno local o Aiven:
```bash
python herramientas/panel-pruebas/panel.py            # http://127.0.0.1:8095
```

## API

| Método y ruta | Alcance | Respuestas |
|---|---|---|
| `POST /api/v1/eventos-guia` | `eventos:escribir` | **202** durable · **200** duplicado · 400 contrato · 401 · 403 (sin alcance o suplantando otro origen) · 413 · 429 + Retry-After · **503 + Retry-After** (nada durable) |
| `GET /api/v1/guias/{numeroGuia}` | `guias:leer` | 200 estado + historial · 400 · 401 · 403 · 404 · 429 |
| `GET /salud/viva` · `GET /salud/lista` | — | Vida (sin dependencias) · Lista (puede guardar de forma durable en algún lado) |

```json
{
  "idEvento": "0199a1b2-7c3d-7e4f-8a9b-0c1d2e3f4a5b",
  "numeroGuia": "TCC123456789",
  "estado": "EN_REPARTO",
  "ocurridoEn": "2026-11-30T10:15:00-05:00",
  "origen": "TMS",
  "novedad": null
}
```

## Servicios locales

| Servicio | URL | |
|---|---|---|
| API (contenedor / desarrollo) | http://localhost:8090 · http://localhost:5013 | |
| Keycloak | http://localhost:8081 | clientes `tms`, `transporte`, `portal-consulta` (secretos solo locales en `infra/keycloak/tcc-realm.json`) |
| Grafana | http://localhost:3000 | tablero "TCC · Plataforma de eventos de guías" |
| Jaeger | http://localhost:16686 | trazas de punta a punta |
| Prometheus | http://localhost:9090 | métricas y 6 alertas por SLO |
| Kafka UI | http://localhost:8080 | tópicos, mensajes, DLQ y lag |

Todos los puertos se publican solo en `127.0.0.1`.

## Estructura

```
tcc-dominio/              Java puro, 0 dependencias: Guia, MaquinaEstados, PoliticaNotificacion
tcc-contratos/            records V1, 0 dependencias
tcc-aplicacion/           casos de uso, puertos y decoradores (sin Spring)
tcc-infraestructura/      adaptadores: Kafka, JDBC, Redis, proveedores, circuitos, telemetría
tcc-api/                  Spring Web MVC + Security: ingesta, consulta, salud
tcc-procesador/           Spring Kafka: inbox + estado + outbox
tcc-notificador/          Spring Kafka: reintentos no bloqueantes, canal alterno, DLQ
tcc-arquitectura-pruebas/ ArchUnit: reglas de la hexagonal
Dockerfile                una imagen distroless por servicio (ARG MODULO) con el agente OpenTelemetry
db/migraciones/           SQL versionado (Flyway), el mismo de la versión .NET
infra/                    docker-compose, Kafka, Keycloak, observabilidad, prueba de humo
pruebas-carga/            k6 y reconciliación (resultados/dotnet: mediciones de la versión .NET)
.github/                  pipeline (GitHub Actions) y Dependabot
docs/                     versión Java, arquitectura, ADR, operación, pruebas, liderazgo, visión, IA
```

## CI/CD
[`.github/workflows/ci.yml`](.github/workflows/ci.yml): `mvnw verify` con avisos como errores y 176 pruebas → Trivy sobre dependencias y configuración → imágenes escaneadas con Trivy (publicadas en GHCR solo desde `main`) → prueba de humo con esas imágenes → staging → producción con aprobación manual y **congelamiento en temporada pico**.

## Documentación

| Documento | Contenido |
|---|---|
| [Versión Java](docs/java.md) | Equivalencias .NET → Java, qué se verificó, diferencias conscientes y limitaciones |
| [Arquitectura](docs/arquitectura.md) | Componentes, garantías, fallas, datos, patrones, escalabilidad, seguridad |
| [Decisiones (ADR)](docs/adr/README.md) | 12 decisiones con contexto, consecuencias y alternativas |
| [Operación](docs/operacion.md) | Manual de guardia: alertas, DLQ, replay, reconciliación |
| [Pruebas](docs/pruebas.md) | Pirámide, caos, carga y reconciliación |
| [Liderazgo](docs/liderazgo.md) · [Visión](docs/vision.md) · [IA](docs/ia.md) | Equipo, 90 días, 18 meses y agentes |
| [Demo en Aiven](docs/demo-aiven.md) | Kafka y PostgreSQL administrados (plan gratuito) con TLS |
| [AGENTS.md](AGENTS.md) | Reglas para agentes de código y personas nuevas |

## Limitaciones conocidas
- **Carga y caos pendientes en Java:** los resultados de carga y caos de [`docs/pruebas.md`](docs/pruebas.md) se midieron con la versión .NET. Los scripts de k6, la reconciliación y el panel sirven sin cambios; falta correrlos contra Java.
- **Capacidad no certificada:** igual que en .NET, las pruebas corren en un portátil que comparte CPU entre todos los componentes.
- **Vaciado de la contingencia uno por uno** (en .NET, en lote): más simple; si tras una caída larga fuera lento, se paraleliza por guía.
- **Proyector de lecturas en Redis, integraciones y agente de novedades:** diseñados, fuera del alcance del ejercicio.
