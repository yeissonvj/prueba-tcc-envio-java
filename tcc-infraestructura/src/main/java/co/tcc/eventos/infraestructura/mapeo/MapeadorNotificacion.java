package co.tcc.eventos.infraestructura.mapeo;

import co.tcc.eventos.contratos.v1.CanalesV1;
import co.tcc.eventos.contratos.v1.EstadoGuiaCambiadoV1;
import co.tcc.eventos.dominio.notificaciones.CambioEstadoGuia;
import co.tcc.eventos.dominio.notificaciones.CanalNotificacion;

public final class MapeadorNotificacion {

    private MapeadorNotificacion() {
    }

    public static CambioEstadoGuia aDominio(EstadoGuiaCambiadoV1 c) {
        return new CambioEstadoGuia(
                c.idEvento(),
                c.numeroGuia(),
                c.estadoAnterior() == null ? null : MapeadorEventoGuia.estadoDesdeTexto(c.estadoAnterior()),
                MapeadorEventoGuia.estadoDesdeTexto(c.estadoNuevo()),
                c.ocurridoEn(),
                c.version());
    }

    public static String canalATexto(CanalNotificacion canal) {
        return switch (canal) {
            case SMS -> CanalesV1.SMS;
            case CORREO -> CanalesV1.CORREO;
        };
    }

    public static CanalNotificacion canalDesdeTexto(String canal) {
        if (CanalesV1.SMS.equals(canal))
            return CanalNotificacion.SMS;
        if (CanalesV1.CORREO.equals(canal))
            return CanalNotificacion.CORREO;
        throw new IllegalArgumentException("Canal no reconocido: '" + canal + "'.");
    }
}
