package co.tcc.eventos.contratos.v1;

import java.time.OffsetDateTime;

/**
 * Mensaje de los tópicos notificaciones.reintento.* y notificaciones.dlq.
 *
 * @param canal      SMS o CORREO (ver {@link CanalesV1})
 * @param intento    reintentos ya hechos por ese canal (0 = primer envío)
 * @param recibidoEn cuándo llegó el cambio a guias.estados.cambiados (mide la latencia real, reintentos
 *                   incluidos). Opcional para ser compatible con mensajes anteriores.
 */
public record NotificacionPendienteV1(EstadoGuiaCambiadoV1 cambio, String canal, int intento, OffsetDateTime recibidoEn) {

    public static final String TIPO = "NotificacionPendienteV1";

    public NotificacionPendienteV1 conIntento(int nuevoIntento) {
        return new NotificacionPendienteV1(cambio, canal, nuevoIntento, recibidoEn);
    }

    public NotificacionPendienteV1 porCanal(String nuevoCanal) {
        return new NotificacionPendienteV1(cambio, nuevoCanal, 0, recibidoEn);
    }
}
