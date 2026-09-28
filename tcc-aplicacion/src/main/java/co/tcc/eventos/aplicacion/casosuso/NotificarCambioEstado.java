package co.tcc.eventos.aplicacion.casosuso;

import co.tcc.eventos.aplicacion.puertos.notificacion.DirectorioContactos;
import co.tcc.eventos.aplicacion.puertos.notificacion.MensajeNotificacion;
import co.tcc.eventos.aplicacion.puertos.notificacion.ProveedorNotificacion;
import co.tcc.eventos.aplicacion.puertos.notificacion.RegistroNotificaciones;
import co.tcc.eventos.dominio.notificaciones.CambioEstadoGuia;
import co.tcc.eventos.dominio.notificaciones.CanalNotificacion;
import co.tcc.eventos.dominio.notificaciones.PoliticaNotificacion;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Envía UNA notificación por un canal. No decide reintentos ni canal alterno: eso es política del host.
 * Las fallas del proveedor se propagan como excepción para que el host las enrute.
 */
public final class NotificarCambioEstado {

    private final RegistroNotificaciones registro;
    private final DirectorioContactos directorio;
    private final Map<CanalNotificacion, ProveedorNotificacion> proveedores = new EnumMap<>(CanalNotificacion.class);

    public NotificarCambioEstado(
            RegistroNotificaciones registro, DirectorioContactos directorio, List<ProveedorNotificacion> proveedores) {
        this.registro = registro;
        this.directorio = directorio;
        for (var proveedor : proveedores)
            if (this.proveedores.put(proveedor.canal(), proveedor) != null)
                throw new IllegalArgumentException("Hay dos proveedores para el canal " + proveedor.canal() + ".");
    }

    public ResultadoNotificacion ejecutar(CambioEstadoGuia cambio, CanalNotificacion canal) {
        if (!PoliticaNotificacion.debeNotificar(cambio.estadoNuevo()))
            return ResultadoNotificacion.NO_APLICA;

        switch (registro.consultar(cambio, canal)) {
            case YA_ENVIADA -> {
                return ResultadoNotificacion.YA_ENVIADA;
            }
            case OBSOLETA -> {
                return ResultadoNotificacion.OBSOLETA;
            }
            case PENDIENTE -> {
            }
        }

        var contacto = directorio.obtener(cambio.numeroGuia());
        var destino = contacto.map(c -> canal == CanalNotificacion.SMS ? c.telefono() : c.correo()).orElse(null);
        if (destino == null || destino.isBlank())
            return ResultadoNotificacion.SIN_DESTINO;

        var mensaje = new MensajeNotificacion(
                PoliticaNotificacion.claveIdempotencia(cambio, canal), destino, PoliticaNotificacion.texto(cambio));
        proveedor(canal).enviar(mensaje);

        // Si el proceso cae aquí, el reintento reenvía con la misma clave y el proveedor lo deduplica.
        registro.registrarEnvio(cambio, canal);
        return ResultadoNotificacion.ENVIADA;
    }

    private ProveedorNotificacion proveedor(CanalNotificacion canal) {
        var proveedor = proveedores.get(canal);
        if (proveedor == null)
            throw new IllegalStateException("No hay proveedor configurado para el canal " + canal + ".");
        return proveedor;
    }
}
