package co.tcc.eventos.aplicacion.puertos.notificacion;

import co.tcc.eventos.dominio.notificaciones.CanalNotificacion;

/**
 * Proveedor externo (SMS, correo). Debe usar claveIdempotencia como llave de idempotencia del proveedor.
 * Falla con {@link ProveedorNoDisponibleException} (timeout, 5xx, 429: reintentar) o
 * {@link DestinoRechazadoException} (número o correo inválido: reintentar no sirve).
 */
public interface ProveedorNotificacion {

    CanalNotificacion canal();

    void enviar(MensajeNotificacion mensaje);
}
