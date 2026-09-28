package co.tcc.eventos.notificador;

import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import co.tcc.eventos.contratos.v1.NotificacionPendienteV1;
import co.tcc.eventos.notificador.enrutamiento.EnrutadorNotificaciones;
import java.util.ArrayList;
import java.util.List;

class EnrutadorEnMemoria implements EnrutadorNotificaciones {

    record Rechazo(String clave, String contenido, String motivo) {
    }

    final List<NotificacionPendienteV1> reintentos = new ArrayList<>();
    final List<Rechazo> dlq = new ArrayList<>();
    int fallasPendientes;

    @Override
    public void programarReintento(NotificacionPendienteV1 pendiente) {
        fallarSiCorresponde();
        reintentos.add(pendiente);
    }

    @Override
    public void enviarADlq(String clave, String contenido, String motivo) {
        fallarSiCorresponde();
        dlq.add(new Rechazo(clave, contenido, motivo));
    }

    private void fallarSiCorresponde() {
        if (fallasPendientes <= 0)
            return;
        fallasPendientes--;
        throw new PublicacionFallidaException("Kafka no disponible (simulado)");
    }
}
