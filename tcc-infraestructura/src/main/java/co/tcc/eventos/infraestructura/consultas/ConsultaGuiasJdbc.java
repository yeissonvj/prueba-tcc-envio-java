package co.tcc.eventos.infraestructura.consultas;

import co.tcc.eventos.contratos.v1.EventoHistorialV1;
import co.tcc.eventos.contratos.v1.GuiaV1;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;

public final class ConsultaGuiasJdbc implements ConsultaGuias {

    public static final int MAXIMO_HISTORIAL = 100;

    private final JdbcClient jdbc;

    public ConsultaGuiasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<GuiaV1> obtener(String numeroGuia) {
        record Estado(String estado, OffsetDateTime ultimoEventoEn, long version) {
        }

        var estado = jdbc.sql("SELECT estado_actual, ultimo_evento_en, version FROM guias WHERE numero_guia = :guia")
                .param("guia", numeroGuia)
                .query((fila, n) -> new Estado(fila.getString(1), fila.getObject(2, OffsetDateTime.class), fila.getLong(3)))
                .optional();
        if (estado.isEmpty())
            return Optional.empty();

        var historial = jdbc.sql("""
                        SELECT id_evento, estado, ocurrido_en, origen, novedad, resultado
                        FROM historial_eventos
                        WHERE numero_guia = :guia
                        ORDER BY ocurrido_en DESC, procesado_en DESC
                        LIMIT :maximo
                        """)
                .param("guia", numeroGuia)
                .param("maximo", MAXIMO_HISTORIAL)
                .query((fila, n) -> new EventoHistorialV1(
                        fila.getObject(1, UUID.class),
                        fila.getString(2),
                        fila.getObject(3, OffsetDateTime.class),
                        fila.getString(4),
                        fila.getString(5),
                        fila.getString(6)))
                .list();

        var guia = estado.get();
        return Optional.of(new GuiaV1(numeroGuia, guia.estado(), guia.ultimoEventoEn(), guia.version(), historial));
    }
}
