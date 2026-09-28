package co.tcc.eventos.infraestructura.notificaciones;

import co.tcc.eventos.aplicacion.puertos.notificacion.MensajeNotificacion;
import co.tcc.eventos.aplicacion.puertos.notificacion.ProveedorNoDisponibleException;
import co.tcc.eventos.aplicacion.puertos.notificacion.ProveedorNotificacion;
import co.tcc.eventos.dominio.notificaciones.CanalNotificacion;
import co.tcc.eventos.infraestructura.resiliencia.Circuitos;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import java.time.Clock;

/**
 * Decorador + Circuit Breaker por proveedor: si el SMS está caído, se deja de insistir durante un tiempo
 * y los mensajes pasan directo a reintento en vez de esperar un timeout cada uno.
 */
public final class ProveedorConCircuito implements ProveedorNotificacion {

    private final ProveedorNotificacion interno;
    private final CircuitBreaker circuito;

    public ProveedorConCircuito(ProveedorNotificacion interno, OpcionesProveedores opciones) {
        this(interno, opciones, Clock.systemUTC());
    }

    public ProveedorConCircuito(ProveedorNotificacion interno, OpcionesProveedores opciones, Clock reloj) {
        this.interno = interno;
        this.circuito = Circuitos.crear(
                "proveedor-" + interno.canal().name().toLowerCase(),
                "Circuito del proveedor de " + interno.canal(),
                "los mensajes pasan a reintento",
                ProveedorNoDisponibleException.class,
                opciones.getCircuitoMinimoEnvios(),
                opciones.getCircuitoVentanaSegundos(),
                opciones.getCircuitoSegundosAbierto(),
                reloj);
    }

    @Override
    public CanalNotificacion canal() {
        return interno.canal();
    }

    @Override
    public void enviar(MensajeNotificacion mensaje) {
        try {
            circuito.executeRunnable(() -> interno.enviar(mensaje));
        } catch (CallNotPermittedException ex) {
            throw new ProveedorNoDisponibleException("Circuito del proveedor de " + canal() + " abierto; no se intentó enviar.", ex);
        }
    }
}
