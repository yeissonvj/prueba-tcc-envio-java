package co.tcc.eventos.aplicacion.falsos;

import co.tcc.eventos.aplicacion.puertos.notificacion.DirectorioContactos;
import co.tcc.eventos.aplicacion.puertos.notificacion.EstadoNotificacion;
import co.tcc.eventos.aplicacion.puertos.notificacion.MensajeNotificacion;
import co.tcc.eventos.aplicacion.puertos.notificacion.ProveedorNotificacion;
import co.tcc.eventos.aplicacion.puertos.notificacion.RegistroNotificaciones;
import co.tcc.eventos.dominio.notificaciones.CambioEstadoGuia;
import co.tcc.eventos.dominio.notificaciones.CanalNotificacion;
import co.tcc.eventos.dominio.notificaciones.Contacto;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

/** Falsos de los puertos de notificación, escritos a mano. */
public final class FalsosNotificacion {

    private FalsosNotificacion() {
    }

    public record Enviada(String guia, long version, CanalNotificacion canal) {
    }

    public static class RegistroEnMemoria implements RegistroNotificaciones {

        public final List<Enviada> enviadas = new ArrayList<>();

        @Override
        public EstadoNotificacion consultar(CambioEstadoGuia cambio, CanalNotificacion canal) {
            if (enviadas.contains(new Enviada(cambio.numeroGuia(), cambio.version(), canal)))
                return EstadoNotificacion.YA_ENVIADA;
            if (enviadas.stream().anyMatch(e -> e.guia().equals(cambio.numeroGuia()) && e.version() > cambio.version()))
                return EstadoNotificacion.OBSOLETA;
            return EstadoNotificacion.PENDIENTE;
        }

        @Override
        public void registrarEnvio(CambioEstadoGuia cambio, CanalNotificacion canal) {
            enviadas.add(new Enviada(cambio.numeroGuia(), cambio.version(), canal));
        }
    }

    public static class DirectorioFijo implements DirectorioContactos {

        private final Contacto contacto;

        public DirectorioFijo(Contacto contacto) {
            this.contacto = contacto;
        }

        @Override
        public Optional<Contacto> obtener(String numeroGuia) {
            return Optional.ofNullable(contacto);
        }
    }

    public static class ProveedorEnMemoria implements ProveedorNotificacion {

        private final CanalNotificacion canal;
        public final List<MensajeNotificacion> enviados = new ArrayList<>();
        /** Si no es null, cada envío lanza lo que devuelva. */
        public Supplier<RuntimeException> falla;

        public ProveedorEnMemoria(CanalNotificacion canal) {
            this.canal = canal;
        }

        @Override
        public CanalNotificacion canal() {
            return canal;
        }

        @Override
        public void enviar(MensajeNotificacion mensaje) {
            if (falla != null)
                throw falla.get();
            enviados.add(mensaje);
        }
    }
}
