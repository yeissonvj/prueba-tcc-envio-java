package co.tcc.eventos.infraestructura.mapeo;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Único lugar con las reglas de serialización de los contratos, iguales a las de la versión .NET
 * (JsonSerializerDefaults.Web): camelCase, fechas ISO-8601 conservando el offset original (-05:00),
 * lectura sin distinguir mayúsculas y campos desconocidos ignorados (evolución compatible del contrato).
 * Así .NET y Java leen y escriben exactamente los mismos mensajes de Kafka.
 */
public final class JsonContratos {

    public static final JsonMapper MAPPER = configurar(JsonMapper.builder()).build();

    private JsonContratos() {
    }

    /** Aplica las reglas a un constructor existente (p. ej. el que usa Spring MVC en la API). */
    public static JsonMapper.Builder configurar(JsonMapper.Builder constructor) {
        return constructor
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .disable(DateTimeFeature.WRITE_DATES_WITH_CONTEXT_TIME_ZONE)
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                .enable(MapperFeature.ACCEPT_CASE_INSENSITIVE_PROPERTIES);
    }

    public static String escribir(Object valor) {
        return MAPPER.writeValueAsString(valor);
    }

    /** Lanza {@link tools.jackson.core.JacksonException} (no verificada) si el texto no es válido. */
    public static <T> T leer(String json, Class<T> tipo) {
        return MAPPER.readValue(json, tipo);
    }
}
