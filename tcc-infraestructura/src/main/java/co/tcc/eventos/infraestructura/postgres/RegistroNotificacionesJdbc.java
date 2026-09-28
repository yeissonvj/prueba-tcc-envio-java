package co.tcc.eventos.infraestructura.postgres;

import co.tcc.eventos.aplicacion.puertos.notificacion.EstadoNotificacion;
import co.tcc.eventos.aplicacion.puertos.notificacion.RegistroNotificaciones;
import co.tcc.eventos.dominio.notificaciones.CambioEstadoGuia;
import co.tcc.eventos.dominio.notificaciones.CanalNotificacion;
import co.tcc.eventos.dominio.notificaciones.PoliticaNotificacion;
import co.tcc.eventos.infraestructura.mapeo.MapeadorNotificacion;
import org.springframework.jdbc.core.simple.JdbcClient;

/** Adaptador de {@link RegistroNotificaciones} sobre la tabla notificaciones_enviadas (migración V3). */
public final class RegistroNotificacionesJdbc implements RegistroNotificaciones {

    private final JdbcClient jdbc;

    public RegistroNotificacionesJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public EstadoNotificacion consultar(CambioEstadoGuia cambio, CanalNotificacion canal) {
        // Las dos preguntas en una consulta: ¿esta ya salió? ¿salió algo más reciente de la guía?
        return jdbc.sql("""
                        SELECT EXISTS (SELECT 1 FROM notificaciones_enviadas WHERE clave = :clave),
                               EXISTS (SELECT 1 FROM notificaciones_enviadas WHERE numero_guia = :guia AND version > :version)
                        """)
                .param("clave", PoliticaNotificacion.claveIdempotencia(cambio, canal))
                .param("guia", cambio.numeroGuia())
                .param("version", cambio.version())
                .query((fila, n) -> fila.getBoolean(1) ? EstadoNotificacion.YA_ENVIADA
                        : fila.getBoolean(2) ? EstadoNotificacion.OBSOLETA
                        : EstadoNotificacion.PENDIENTE)
                .single();
    }

    @Override
    public void registrarEnvio(CambioEstadoGuia cambio, CanalNotificacion canal) {
        jdbc.sql("""
                        INSERT INTO notificaciones_enviadas (clave, numero_guia, version, canal)
                        VALUES (:clave, :guia, :version, :canal)
                        ON CONFLICT (clave) DO NOTHING
                        """)
                .param("clave", PoliticaNotificacion.claveIdempotencia(cambio, canal))
                .param("guia", cambio.numeroGuia())
                .param("version", cambio.version())
                .param("canal", MapeadorNotificacion.canalATexto(canal))
                .update();
    }
}
