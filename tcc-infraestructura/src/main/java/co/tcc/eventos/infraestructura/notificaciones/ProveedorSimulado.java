package co.tcc.eventos.infraestructura.notificaciones;

import co.tcc.eventos.aplicacion.puertos.notificacion.DestinoRechazadoException;
import co.tcc.eventos.aplicacion.puertos.notificacion.MensajeNotificacion;
import co.tcc.eventos.aplicacion.puertos.notificacion.ProveedorNoDisponibleException;
import co.tcc.eventos.aplicacion.puertos.notificacion.ProveedorNotificacion;
import co.tcc.eventos.dominio.notificaciones.CanalNotificacion;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proveedor de SMS o correo simulado. Se comporta como uno real en lo que importa para las pruebas:
 * puede estar caído, fallar al azar, rechazar destinos inválidos y deduplicar por clave de idempotencia.
 */
public final class ProveedorSimulado implements ProveedorNotificacion {

    private static final Logger log = LoggerFactory.getLogger(ProveedorSimulado.class);

    private final CanalNotificacion canal;
    private final OpcionesProveedorSimulado opciones;

    // Los proveedores reales recuerdan las claves de idempotencia un tiempo; aquí, mientras vive el proceso.
    private final Set<String> clavesAceptadas = ConcurrentHashMap.newKeySet();

    public ProveedorSimulado(CanalNotificacion canal, OpcionesProveedorSimulado opciones) {
        this.canal = canal;
        this.opciones = opciones;
    }

    @Override
    public CanalNotificacion canal() {
        return canal;
    }

    @Override
    public void enviar(MensajeNotificacion mensaje) {
        esperar(opciones.getLatenciaMs());

        if (opciones.isCaido())
            throw new ProveedorNoDisponibleException("Proveedor de " + canal + " no disponible (HTTP 503 simulado).");
        if (opciones.getTasaFallas() > 0 && ThreadLocalRandom.current().nextDouble() < opciones.getTasaFallas())
            throw new ProveedorNoDisponibleException("Proveedor de " + canal + ": timeout simulado.");
        if (mensaje.destino().startsWith("000"))
            throw new DestinoRechazadoException("Proveedor de " + canal + ": destino inválido.");

        if (!clavesAceptadas.add(mensaje.claveIdempotencia())) {
            log.info("{}: clave {} repetida, el proveedor la ignora", canal, mensaje.claveIdempotencia());
            return;
        }

        // Nunca el destino completo en los logs (Ley 1581).
        log.info("{} a {} [{}]: {}", canal, enmascarar(mensaje.destino()), mensaje.claveIdempotencia(), mensaje.texto());
    }

    public static String enmascarar(String destino) {
        var arroba = destino.indexOf('@');
        if (arroba > 0)
            return destino.charAt(0) + "***" + destino.substring(arroba);
        return destino.length() <= 6 ? "***" : destino.substring(0, 3) + "****" + destino.substring(destino.length() - 3);
    }

    private static void esperar(int ms) {
        if (ms <= 0)
            return;
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new ProveedorNoDisponibleException("Envío interrumpido.", ex);
        }
    }
}
