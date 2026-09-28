package co.tcc.eventos.infraestructura.postgres;

import co.tcc.eventos.aplicacion.puertos.AlmacenContingencia;
import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import co.tcc.eventos.aplicacion.puertos.PublicadorEventos;
import co.tcc.eventos.contratos.v1.EventoGuiaV1;
import co.tcc.eventos.dominio.EventoGuia;
import co.tcc.eventos.infraestructura.mapeo.JsonContratos;
import co.tcc.eventos.infraestructura.mapeo.MapeadorEventoGuia;
import co.tcc.eventos.infraestructura.observabilidad.Telemetria;
import java.util.ArrayList;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Adaptador: contingencia en PostgreSQL (tabla contingencia_eventos, migración V1).
 * SQL parametrizado siempre: los datos del evento nunca se concatenan en la consulta.
 */
public final class AlmacenContingenciaJdbc implements AlmacenContingencia {

    private final JdbcClient jdbc;
    private final TransactionTemplate transaccion;

    public AlmacenContingenciaJdbc(JdbcClient jdbc, TransactionTemplate transaccion) {
        this.jdbc = jdbc;
        this.transaccion = transaccion;
    }

    @Override
    public void guardar(EventoGuia evento) {
        jdbc.sql("""
                        INSERT INTO contingencia_eventos (id_evento, numero_guia, carga)
                        VALUES (:id, :guia, CAST(:carga AS jsonb))
                        ON CONFLICT (id_evento) DO NOTHING
                        """)
                .param("id", evento.idEvento())
                .param("guia", evento.numeroGuia())
                .param("carga", JsonContratos.escribir(MapeadorEventoGuia.aContrato(evento)))
                .update();
        Telemetria.EVENTOS_EN_CONTINGENCIA.add(1);
    }

    @Override
    public int reenviarPendientes(PublicadorEventos publicador, int maximo) {
        var reenviados = transaccion.execute(estado -> {
            // SKIP LOCKED: varias instancias pueden vaciar la tabla a la vez sin tomar las mismas filas.
            var pendientes = jdbc.sql("""
                            SELECT id_evento, carga::text
                            FROM contingencia_eventos
                            ORDER BY recibido_en, id_evento
                            LIMIT :maximo
                            FOR UPDATE SKIP LOCKED
                            """)
                    .param("maximo", maximo)
                    .query((fila, n) -> MapeadorEventoGuia.aDominio(JsonContratos.leer(fila.getString(2), EventoGuiaV1.class)))
                    .list();

            if (pendientes.isEmpty())
                return 0;

            // En orden de llegada: el publicador espera la confirmación de cada uno. Si uno falla,
            // queda en la tabla para el siguiente ciclo; los ya publicados se borran.
            var publicados = new ArrayList<UUID>(pendientes.size());
            for (var evento : pendientes) {
                try {
                    publicador.publicar(evento);
                    publicados.add(evento.idEvento());
                } catch (PublicacionFallidaException ignorada) {
                    // Queda para el siguiente ciclo.
                }
            }

            if (!publicados.isEmpty())
                jdbc.sql("DELETE FROM contingencia_eventos WHERE id_evento IN (:ids)")
                        .param("ids", publicados)
                        .update();
            return publicados.size();
        });
        return reenviados == null ? 0 : reenviados;
    }
}
