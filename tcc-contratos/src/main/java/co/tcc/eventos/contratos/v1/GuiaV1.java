package co.tcc.eventos.contratos.v1;

import java.time.OffsetDateTime;
import java.util.List;

/** Respuesta de GET /api/v1/guias/{numeroGuia}: estado actual y los eventos más recientes (máximo 100). */
public record GuiaV1(
        String numeroGuia,
        String estadoActual,
        OffsetDateTime ultimoEventoEn,
        long version,
        List<EventoHistorialV1> historial) {

    public GuiaV1 {
        historial = List.copyOf(historial);
    }
}
