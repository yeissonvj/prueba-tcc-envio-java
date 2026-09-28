package co.tcc.eventos.api.validacion;

import co.tcc.eventos.contratos.v1.EstadosV1;
import co.tcc.eventos.contratos.v1.EventoGuiaV1;
import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Valida la forma del evento en la frontera. Las reglas de negocio
 * (transiciones, eventos tardíos) NO van aquí: las decide el procesador.
 * Validador propio (no Bean Validation) para reportar exactamente los mismos errores que la versión .NET.
 */
public final class ValidadorEventoGuiaV1 {

    public static final int LONGITUD_MAXIMA_GUIA = 30;
    public static final int LONGITUD_MAXIMA_ORIGEN = 30;
    public static final int LONGITUD_MAXIMA_NOVEDAD = 500;

    // Tolerancia por relojes desfasados en dispositivos de mensajeros.
    public static final Duration TOLERANCIA_FUTURO = Duration.ofMinutes(5);

    private static final UUID VACIO = new UUID(0, 0);

    private final Clock reloj;

    public ValidadorEventoGuiaV1(Clock reloj) {
        this.reloj = reloj;
    }

    /** Misma regla para la ingesta y la consulta: un número que no puede recibirse tampoco se consulta. */
    public static boolean esNumeroGuiaValido(String numeroGuia) {
        return numeroGuia != null
                && !numeroGuia.isBlank()
                && numeroGuia.length() <= LONGITUD_MAXIMA_GUIA
                && numeroGuia.chars().allMatch(c -> (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9'));
    }

    /** Errores por campo (vacío = válido). */
    public Map<String, List<String>> validar(EventoGuiaV1 evento) {
        var errores = new LinkedHashMap<String, List<String>>();

        if (evento.idEvento() == null || VACIO.equals(evento.idEvento()))
            errores.put("idEvento", List.of("Es obligatorio y no puede ser vacío."));

        if (vacio(evento.numeroGuia()))
            errores.put("numeroGuia", List.of("Es obligatorio."));
        else if (!esNumeroGuiaValido(evento.numeroGuia()))
            errores.put("numeroGuia", List.of("Solo letras y números, máximo " + LONGITUD_MAXIMA_GUIA + " caracteres."));

        if (vacio(evento.estado()))
            errores.put("estado", List.of("Es obligatorio."));
        else if (!EstadosV1.TODOS.contains(evento.estado()))
            errores.put("estado", List.of("Valor no reconocido. Permitidos: " + String.join(", ", EstadosV1.TODOS) + "."));

        if (evento.ocurridoEn() == null)
            errores.put("ocurridoEn", List.of("Es obligatorio."));
        else if (evento.ocurridoEn().isAfter(OffsetDateTime.now(reloj).plus(TOLERANCIA_FUTURO)))
            errores.put("ocurridoEn", List.of("No puede estar en el futuro."));

        if (vacio(evento.origen()))
            errores.put("origen", List.of("Es obligatorio."));
        else if (evento.origen().length() > LONGITUD_MAXIMA_ORIGEN)
            errores.put("origen", List.of("Máximo " + LONGITUD_MAXIMA_ORIGEN + " caracteres."));

        if (EstadosV1.NOVEDAD.equals(evento.estado()) && vacio(evento.novedad()))
            errores.put("novedad", List.of("Es obligatoria cuando el estado es NOVEDAD."));
        else if (evento.novedad() != null && evento.novedad().length() > LONGITUD_MAXIMA_NOVEDAD)
            errores.put("novedad", List.of("Máximo " + LONGITUD_MAXIMA_NOVEDAD + " caracteres."));

        return errores;
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
