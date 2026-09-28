package co.tcc.eventos.notificador.enrutamiento;

import co.tcc.eventos.contratos.v1.NotificacionPendienteV1;

/**
 * Mueve una notificación a su siguiente etapa. Solo termina con éxito si el mensaje quedó guardado:
 * de lo contrario el offset del mensaje actual no puede avanzar.
 */
public interface EnrutadorNotificaciones {

    /** Publica en la etapa de reintento correspondiente a pendiente.intento() (1 = primera etapa). */
    void programarReintento(NotificacionPendienteV1 pendiente);

    void enviarADlq(String clave, String contenido, String motivo);
}
