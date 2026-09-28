package co.tcc.eventos.infraestructura.postgres;

import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import co.tcc.eventos.infraestructura.kafka.OpcionesKafka;
import co.tcc.eventos.infraestructura.kafka.ProductorKafka;
import co.tcc.eventos.infraestructura.observabilidad.Telemetria;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.Future;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Publica la bandeja de salida (outbox) en guias.estados.cambiados y borra lo publicado.
 * Un solo relay activo a la vez (advisory lock): si varias instancias publicaran en paralelo,
 * dos cambios de la misma guía podrían salir desordenados (ADR-0006).
 */
public final class RelayBandejaSalida {

    // Identificador arbitrario y fijo del candado "relay de bandeja de salida" (el mismo que en .NET).
    private static final long CLAVE_CANDADO = 7_100_600_001L;

    private record Pendiente(long id, String numeroGuia, String tipo, String carga, String traza) {
    }

    private record Envio(long id, String numeroGuia, Future<RecordMetadata> futuro) {
    }

    private final JdbcClient jdbc;
    private final TransactionTemplate transaccion;
    private final ProductorKafka productor;
    private final OpcionesKafka opciones;

    public RelayBandejaSalida(JdbcClient jdbc, TransactionTemplate transaccion, ProductorKafka productor, OpcionesKafka opciones) {
        this.jdbc = jdbc;
        this.transaccion = transaccion;
        this.productor = productor;
        this.opciones = opciones;
    }

    /** @return cuántos mensajes publicó; 0 si no había pendientes u otra instancia tiene el candado */
    public int publicarPendientes(int maximo) {
        var publicados = transaccion.execute(estado -> {
            // Candado de transacción: se libera solo al terminar (commit, rollback o caída de la conexión).
            var candado = jdbc.sql("SELECT pg_try_advisory_xact_lock(:clave)")
                    .param("clave", CLAVE_CANDADO)
                    .query(Boolean.class)
                    .single();
            if (!candado)
                return 0;

            var pendientes = jdbc.sql("SELECT id, numero_guia, tipo, carga::text, contexto_traza FROM bandeja_salida ORDER BY id LIMIT :maximo")
                    .param("maximo", maximo)
                    .query((fila, n) -> new Pendiente(fila.getLong(1), fila.getString(2), fila.getString(3), fila.getString(4), fila.getString(5)))
                    .list();
            if (pendientes.isEmpty())
                return 0;

            // Se inician en orden de id y se esperan juntos: el productor idempotente conserva el orden por
            // partición, y si un mensaje de una partición falla, los siguientes de esa partición también fallan.
            var envios = new ArrayList<Envio>(pendientes.size());
            for (var p : pendientes) {
                try {
                    // El contexto de traza guardado con el cambio reengancha la publicación a la traza del evento.
                    var futuro = Telemetria.enContexto(p.traza(), () -> productor.enviar(
                            opciones.getTopicoEstadosCambiados(), p.numeroGuia(), p.carga(), Map.of("contrato", p.tipo())));
                    envios.add(new Envio(p.id(), p.numeroGuia(), futuro));
                } catch (PublicacionFallidaException ex) {
                    break; // Kafka no acepta envíos: lo que falta queda para el siguiente ciclo, en orden.
                }
            }

            var confirmados = new ArrayList<Long>(envios.size());
            for (var envio : envios) {
                try {
                    productor.esperar(envio.futuro(), opciones.getTopicoEstadosCambiados(), envio.numeroGuia());
                    confirmados.add(envio.id());
                } catch (PublicacionFallidaException ignorada) {
                    // Queda en la bandeja para el siguiente ciclo.
                }
            }

            if (!confirmados.isEmpty())
                jdbc.sql("DELETE FROM bandeja_salida WHERE id IN (:ids)").param("ids", confirmados).update();
            return confirmados.size();
        });
        return publicados == null ? 0 : publicados;
    }
}
