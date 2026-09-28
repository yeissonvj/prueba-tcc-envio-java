package co.tcc.eventos.contratos.v1;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Mensaje de guias.estados.cambiados: solo cambios validados y aplicados.
 * {@code version} es consecutiva por guía: un consumidor puede descartar cualquier mensaje con versión menor
 * o igual a la última que procesó, y usar (numeroGuia, version) como llave de idempotencia.
 *
 * @param estadoAnterior null cuando el cambio creó la guía
 */
public record EstadoGuiaCambiadoV1(
        UUID idEvento,
        String numeroGuia,
        String estadoAnterior,
        String estadoNuevo,
        OffsetDateTime ocurridoEn,
        String origen,
        String novedad,
        long version) {

    public static final String TIPO = "EstadoGuiaCambiadoV1";
}
