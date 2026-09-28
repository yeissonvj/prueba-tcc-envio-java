package co.tcc.eventos.dominio.notificaciones;

import static co.tcc.eventos.dominio.EstadoGuia.*;

import co.tcc.eventos.dominio.EstadoGuia;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

public final class PoliticaNotificacion {

    // Solo lo que al cliente le importa; los movimientos internos entre bodegas no generan mensajes.
    private static final Set<EstadoGuia> ESTADOS_QUE_NOTIFICAN =
            EnumSet.of(RECOGIDA, EN_REPARTO, ENTREGADA, NOVEDAD, DEVUELTA);

    private PoliticaNotificacion() {
    }

    public static boolean debeNotificar(EstadoGuia estado) {
        return ESTADOS_QUE_NOTIFICAN.contains(estado);
    }

    /** Una notificación por guía, versión del cambio y canal: nunca se envía dos veces la misma. */
    public static String claveIdempotencia(CambioEstadoGuia cambio, CanalNotificacion canal) {
        return cambio.numeroGuia() + ":" + cambio.version() + ":" + canal.name();
    }

    public static Optional<CanalNotificacion> canalAlterno(CanalNotificacion canal) {
        return canal == CanalNotificacion.SMS ? Optional.of(CanalNotificacion.CORREO) : Optional.empty();
    }

    public static String texto(CambioEstadoGuia cambio) {
        var guia = cambio.numeroGuia();
        return switch (cambio.estadoNuevo()) {
            case RECOGIDA -> "TCC: recogimos tu envío " + guia + ".";
            case EN_REPARTO -> "TCC: tu envío " + guia + " está en reparto y llega hoy.";
            case ENTREGADA -> "TCC: tu envío " + guia + " fue entregado.";
            case NOVEDAD -> "TCC: tu envío " + guia + " tiene una novedad. Consulta tcc.com.co.";
            case DEVUELTA -> "TCC: tu envío " + guia + " fue devuelto al remitente.";
            default -> throw new IllegalArgumentException("Estado sin notificación: " + cambio.estadoNuevo());
        };
    }
}
