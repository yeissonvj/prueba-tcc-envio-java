package co.tcc.eventos.aplicacion.puertos.notificacion;

import co.tcc.eventos.dominio.notificaciones.CambioEstadoGuia;
import co.tcc.eventos.dominio.notificaciones.CanalNotificacion;

public interface RegistroNotificaciones {

    /** YA_ENVIADA si esa clave ya se envió; OBSOLETA si ya se notificó una versión más reciente de la guía. */
    EstadoNotificacion consultar(CambioEstadoGuia cambio, CanalNotificacion canal);

    void registrarEnvio(CambioEstadoGuia cambio, CanalNotificacion canal);
}
