package co.tcc.eventos.infraestructura.observabilidad;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Instrumentos de métricas propios y propagación de trazas, con la API de OpenTelemetry.
 * Sin el agente es un no-op (pruebas); con el agente (imágenes Docker) todo se exporta por OTLP al Collector.
 * Los nombres son los mismos de la versión .NET, así el tablero de Grafana y las alertas sirven sin cambios:
 * tcc.eventos.recibidos → tcc_eventos_recibidos_total, tcc.latencia.estado (s) → tcc_latencia_estado_seconds.
 */
public final class Telemetria {

    public static final String NOMBRE = "TccEventos";

    // Latencias en segundos: los cubos por defecto están pensados para milisegundos.
    private static final List<Double> CUBOS_SEGUNDOS =
            List.of(0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1.0, 2.5, 5.0, 10.0, 30.0, 60.0, 120.0, 300.0);

    private static final Meter METRICAS = GlobalOpenTelemetry.getMeter(NOMBRE);

    /** resultado: aceptado | duplicado | rechazado | no_durable */
    public static final LongCounter EVENTOS_RECIBIDOS = contador(
            "tcc.eventos.recibidos", "{evento}", "Eventos recibidos por la API de ingesta.");

    public static final LongCounter EVENTOS_EN_CONTINGENCIA = contador(
            "tcc.eventos.contingencia", "{evento}", "Eventos guardados en contingencia porque Kafka no confirmó.");

    /** resultado: aplicado | tardio | transicion_invalida | duplicado */
    public static final LongCounter EVENTOS_PROCESADOS = contador(
            "tcc.eventos.procesados", "{evento}", "Eventos procesados por el procesador de estado.");

    /** SLO "estado visible p95 < 5 s": desde que Kafka recibió el evento hasta el commit del nuevo estado. */
    public static final DoubleHistogram LATENCIA_ESTADO = histograma(
            "tcc.latencia.estado", "Latencia desde la ingesta hasta el estado visible.");

    /** canal: sms | correo; resultado: enviada | ya_enviada | obsoleta | no_aplica | sin_destino | reintento | fallida */
    public static final LongCounter NOTIFICACIONES = contador(
            "tcc.notificaciones", "{notificacion}", "Intentos de notificación por canal y resultado.");

    /** SLO "notificación p95 < 60 s": desde que el cambio llegó a guias.estados.cambiados hasta el envío. */
    public static final DoubleHistogram LATENCIA_NOTIFICACION = histograma(
            "tcc.latencia.notificacion", "Latencia desde el cambio de estado hasta la notificación.");

    /** origen: procesador | notificador */
    public static final LongCounter MENSAJES_DLQ = contador(
            "tcc.dlq", "{mensaje}", "Mensajes enviados a una DLQ.");

    private static final TextMapGetter<Map<String, String>> LECTOR = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(Map<String, String> portador) {
            return portador.keySet();
        }

        @Override
        public String get(Map<String, String> portador, String clave) {
            return portador == null ? null : portador.get(clave);
        }
    };

    private Telemetria() {
    }

    public static Attributes etiqueta(String nombre, String valor) {
        return Attributes.of(AttributeKey.stringKey(nombre), valor);
    }

    public static Attributes etiquetas(String nombre1, String valor1, String nombre2, String valor2) {
        return Attributes.of(AttributeKey.stringKey(nombre1), valor1, AttributeKey.stringKey(nombre2), valor2);
    }

    /** Etiqueta en minúsculas a partir de un enum (TRANSICION_INVALIDA → transicion_invalida). */
    public static String texto(Enum<?> valor) {
        return valor.name().toLowerCase(Locale.ROOT);
    }

    /**
     * Contexto de traza actual en formato W3C (traceparent), o null si no hay traza.
     * El outbox lo guarda junto al cambio para que la traza no se "corte" en la base de datos.
     */
    public static String traceparentActual() {
        var portador = new HashMap<String, String>();
        W3CTraceContextPropagator.getInstance().inject(Context.current(), portador, Map::put);
        return portador.get("traceparent");
    }

    /**
     * Ejecuta {@code accion} colgando de la traza {@code traceparent} (si la hay). Con el agente, lo que
     * se publique en Kafka dentro de la acción queda como hijo de esa traza.
     */
    public static <T> T enContexto(String traceparent, Supplier<T> accion) {
        if (traceparent == null || traceparent.isBlank())
            return accion.get();
        var contexto = W3CTraceContextPropagator.getInstance()
                .extract(Context.current(), Map.of("traceparent", traceparent), LECTOR);
        try (var alcance = contexto.makeCurrent()) {
            return accion.get();
        }
    }

    private static LongCounter contador(String nombre, String unidad, String descripcion) {
        return METRICAS.counterBuilder(nombre).setUnit(unidad).setDescription(descripcion).build();
    }

    private static DoubleHistogram histograma(String nombre, String descripcion) {
        return METRICAS.histogramBuilder(nombre)
                .setUnit("s")
                .setDescription(descripcion)
                .setExplicitBucketBoundariesAdvice(CUBOS_SEGUNDOS)
                .build();
    }
}
