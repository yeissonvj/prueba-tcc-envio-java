# Guía para agentes de código (y personas nuevas)

Contexto del repositorio en [`README.md`](README.md), [`docs/arquitectura.md`](docs/arquitectura.md) y [`docs/java.md`](docs/java.md).

## Reglas
- **Nombres en español sin tildes ni eñes** en código, tópicos, tablas y columnas ([ADR-0012](docs/adr/0012-nombres-en-espanol-y-diseno-portable.md)).
- **Arquitectura hexagonal:** `tcc-dominio` no depende de nada (Java puro); `tcc-aplicacion` solo de `tcc-dominio` y `slf4j-api`; el núcleo no conoce Spring; `tcc-infraestructura` nunca depende de un host. Lo imponen los módulos Maven y lo verifica `tcc-arquitectura-pruebas` (ArchUnit).
- **Casos de uso sin anotaciones de Spring:** se crean con `@Bean` en la raíz de composición de cada host (`Configuracion*`).
- **Compatibilidad con la versión .NET:** mismos contratos JSON, tópicos, grupos de consumo, encabezados y tablas. Ambas versiones pueden convivir en el mismo clúster. `ContratoJsonPruebas` es la referencia.
- **Esquema:** solo con una nueva migración `db/migraciones/V{n}__*.sql`, compatible con la versión anterior (expand/contract). Nunca editar una migración ya aplicada. Ningún servicio migra al arrancar.
- **Contratos (`tcc-contratos`):** cambios solo compatibles hacia atrás (campos opcionales nuevos).
- **Kafka:** publicar siempre con `ProductorKafka` y armar consumidores con `OpcionesKafka.propiedadesConsumidor` (durabilidad, offsets y conexión en un solo lugar).
- **JSON:** siempre con `JsonContratos` (conserva el huso horario original, igual que .NET).
- **Pruebas:** JUnit 5 + AssertJ y falsos escritos a mano (sin Mockito); integración con Testcontainers; toda corrección de bug trae su prueba. Las clases de prueba terminan en `Pruebas`.
- **Sin secretos ni datos reales** en código, pruebas o prompts. Los secretos de `infra/keycloak/tcc-realm.json` y de los perfiles `local` son solo locales.
- **Avisos = errores:** el compilador corre con `-Werror`.
- **Archivos UTF-8 sin BOM.** En Windows PowerShell 5.1 no reescribir archivos con `Get-Content`/`Set-Content` (corrompe las tildes).

## Antes de abrir un PR
```bash
./mvnw verify
docker compose -f infra/docker-compose.yml --profile aplicaciones up -d --build
API=http://localhost:8090 ./infra/pruebas/humo.sh
```
Quien abre el PR responde por el código, lo haya escrito una persona o un agente.
