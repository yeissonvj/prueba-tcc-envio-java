package co.tcc.eventos.contratos.v1;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento de estado de una guía, tal como lo envían los sistemas origen.
 * Los campos pueden llegar null desde afuera: la validación de la frontera decide si es aceptable.
 *
 * @param novedad opcional; obligatoria cuando el estado es NOVEDAD
 */
public record EventoGuiaV1(
        UUID idEvento,
        String numeroGuia,
        String estado,
        OffsetDateTime ocurridoEn,
        String origen,
        String novedad) {

    public static final String TIPO = "EventoGuiaV1";
}
