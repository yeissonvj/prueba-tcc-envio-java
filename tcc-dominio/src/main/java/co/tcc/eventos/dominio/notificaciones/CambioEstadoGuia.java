package co.tcc.eventos.dominio.notificaciones;

import co.tcc.eventos.dominio.EstadoGuia;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Un cambio de estado ya aplicado por el procesador (lo que viaja en guias.estados.cambiados).
 *
 * @param estadoAnterior null cuando el cambio creó la guía
 */
public record CambioEstadoGuia(
        UUID idEvento,
        String numeroGuia,
        EstadoGuia estadoAnterior,
        EstadoGuia estadoNuevo,
        OffsetDateTime ocurridoEn,
        long version) {
}
