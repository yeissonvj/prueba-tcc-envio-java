package co.tcc.eventos.infraestructura.postgres;

import co.tcc.eventos.aplicacion.puertos.ConflictoConcurrenciaException;
import co.tcc.eventos.aplicacion.puertos.RepositorioGuias;
import co.tcc.eventos.contratos.v1.EstadoGuiaCambiadoV1;
import co.tcc.eventos.dominio.EstadoGuia;
import co.tcc.eventos.dominio.EventoGuia;
import co.tcc.eventos.dominio.Guia;
import co.tcc.eventos.dominio.ResultadoAplicacion;
import co.tcc.eventos.infraestructura.mapeo.JsonContratos;
import co.tcc.eventos.infraestructura.mapeo.MapeadorEventoGuia;
import co.tcc.eventos.infraestructura.observabilidad.Telemetria;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Adaptador de {@link RepositorioGuias} sobre las tablas de la migración V2.
 * Historial (inbox) + estado + bandeja de salida (outbox) en UNA transacción.
 * La garantía la dan las llaves primarias y la versión: si otra instancia se adelantó, alguna sentencia
 * afecta 0 filas, se lanza el conflicto y la transacción completa se revierte.
 */
public final class RepositorioGuiasJdbc implements RepositorioGuias {

    private final JdbcClient jdbc;
    private final TransactionTemplate transaccion;

    public RepositorioGuiasJdbc(JdbcClient jdbc, TransactionTemplate transaccion) {
        this.jdbc = jdbc;
        this.transaccion = transaccion;
    }

    @Override
    public boolean existeEvento(UUID idEvento) {
        return jdbc.sql("SELECT EXISTS (SELECT 1 FROM historial_eventos WHERE id_evento = :id)")
                .param("id", idEvento)
                .query(Boolean.class)
                .single();
    }

    @Override
    public Optional<Guia> obtener(String numeroGuia) {
        return jdbc.sql("SELECT estado_actual, ultimo_evento_en, version FROM guias WHERE numero_guia = :guia")
                .param("guia", numeroGuia)
                .query((fila, n) -> Guia.reconstruir(
                        numeroGuia,
                        MapeadorEventoGuia.estadoDesdeTexto(fila.getString(1)),
                        fila.getObject(2, OffsetDateTime.class),
                        fila.getLong(3)))
                .optional();
    }

    @Override
    public void guardar(Guia guia, EventoGuia evento, ResultadoAplicacion resultado, EstadoGuia estadoAnterior) {
        transaccion.executeWithoutResult(estado -> {
            // ON CONFLICT DO NOTHING: si otra instancia ya lo guardó, afecta 0 filas (conflicto, no excepción).
            var historial = jdbc.sql("""
                            INSERT INTO historial_eventos (id_evento, numero_guia, estado, ocurrido_en, origen, novedad, resultado)
                            VALUES (:id, :guia, :estado, :ocurrido, :origen, :novedad, :resultado)
                            ON CONFLICT (id_evento) DO NOTHING
                            """)
                    .param("id", evento.idEvento())
                    .param("guia", evento.numeroGuia())
                    .param("estado", MapeadorEventoGuia.estadoATexto(evento.estado()))
                    .param("ocurrido", evento.ocurridoEn())
                    .param("origen", evento.origen())
                    .param("novedad", evento.novedad())
                    .param("resultado", MapeadorEventoGuia.resultadoATexto(resultado))
                    .update();
            if (historial != 1)
                throw new ConflictoConcurrenciaException(guia.numeroGuia());

            if (resultado != ResultadoAplicacion.APLICADO)
                return; // tardío o inválido: solo historial

            var filas = estadoAnterior == null ? insertarGuia(guia) : actualizarGuia(guia);
            if (filas != 1)
                throw new ConflictoConcurrenciaException(guia.numeroGuia());

            insertarEnBandejaSalida(MapeadorEventoGuia.aCambioEstado(guia, evento, estadoAnterior));
        });
    }

    private int insertarGuia(Guia guia) {
        return jdbc.sql("""
                        INSERT INTO guias (numero_guia, estado_actual, ultimo_evento_en, version)
                        VALUES (:guia, :estado, :ultimo, :version)
                        ON CONFLICT (numero_guia) DO NOTHING
                        """)
                .param("guia", guia.numeroGuia())
                .param("estado", MapeadorEventoGuia.estadoATexto(guia.estadoActual()))
                .param("ultimo", guia.ultimoEventoEn())
                .param("version", guia.version())
                .update();
    }

    // Concurrencia optimista: solo actualiza si nadie cambió la versión que se leyó.
    private int actualizarGuia(Guia guia) {
        return jdbc.sql("""
                        UPDATE guias
                        SET estado_actual = :estado, ultimo_evento_en = :ultimo, version = :version, actualizado_en = now()
                        WHERE numero_guia = :guia AND version = :anterior
                        """)
                .param("guia", guia.numeroGuia())
                .param("estado", MapeadorEventoGuia.estadoATexto(guia.estadoActual()))
                .param("ultimo", guia.ultimoEventoEn())
                .param("version", guia.version())
                .param("anterior", guia.version() - 1)
                .update();
    }

    private void insertarEnBandejaSalida(EstadoGuiaCambiadoV1 cambio) {
        jdbc.sql("""
                        INSERT INTO bandeja_salida (numero_guia, tipo, carga, contexto_traza)
                        VALUES (:guia, :tipo, CAST(:carga AS jsonb), :traza)
                        """)
                .param("guia", cambio.numeroGuia())
                .param("tipo", EstadoGuiaCambiadoV1.TIPO)
                .param("carga", JsonContratos.escribir(cambio))
                // La traza del evento viaja con el cambio: el relay la retoma al publicar.
                .param("traza", Telemetria.traceparentActual())
                .update();
    }
}
