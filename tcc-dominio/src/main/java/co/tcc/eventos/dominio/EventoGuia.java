package co.tcc.eventos.dominio;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Algo que le ocurrió a una guía en un momento dado.
 * Es inmutable: un hecho que ya pasó no se modifica.
 *
 * @param novedad opcional (null cuando no aplica)
 */
public record EventoGuia(
        UUID idEvento,
        String numeroGuia,
        EstadoGuia estado,
        OffsetDateTime ocurridoEn,
        String origen,
        String novedad) {

    public EventoGuia {
        Objects.requireNonNull(idEvento, "idEvento");
        Objects.requireNonNull(numeroGuia, "numeroGuia");
        Objects.requireNonNull(estado, "estado");
        Objects.requireNonNull(ocurridoEn, "ocurridoEn");
        Objects.requireNonNull(origen, "origen");
    }

    public EventoGuia(UUID idEvento, String numeroGuia, EstadoGuia estado, OffsetDateTime ocurridoEn, String origen) {
        this(idEvento, numeroGuia, estado, ocurridoEn, origen, null);
    }
}
