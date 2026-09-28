package co.tcc.eventos.contratos.v1;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Resultado: APLICADO (cambió el estado), TARDIO (llegó después de uno más reciente)
 * o TRANSICION_INVALIDA (no respeta la máquina de estados). Todos quedan para auditoría.
 */
public record EventoHistorialV1(
        UUID idEvento,
        String estado,
        OffsetDateTime ocurridoEn,
        String origen,
        String novedad,
        String resultado) {
}
