package co.tcc.eventos.api.salud;

import co.tcc.eventos.infraestructura.kafka.OpcionesKafka;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.DescribeTopicsOptions;

/**
 * Kafka está disponible solo si puede aceptar escrituras con acks=all: cada partición del tópico
 * debe tener al menos min.insync.replicas réplicas sincronizadas. Que "responda" no basta.
 */
public final class SondaKafka implements Sonda, AutoCloseable {

    private static final int LIMITE_MS = 2000;

    private final OpcionesKafka opciones;
    private volatile Admin admin;

    public SondaKafka(OpcionesKafka opciones) {
        this.opciones = opciones;
    }

    @Override
    public boolean disponible() {
        try {
            var descripcion = admin().describeTopics(List.of(opciones.getTopicoEventosRecibidos()),
                            new DescribeTopicsOptions().timeoutMs(LIMITE_MS))
                    .allTopicNames()
                    .get(LIMITE_MS, TimeUnit.MILLISECONDS)
                    .get(opciones.getTopicoEventosRecibidos());

            return descripcion != null
                    && !descripcion.partitions().isEmpty()
                    && descripcion.partitions().stream().allMatch(p -> p.isr().size() >= opciones.getReplicasMinimasSincronizadas());
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            return false;
        }
    }

    private Admin admin() {
        if (admin == null) {
            synchronized (this) {
                if (admin == null) {
                    var propiedades = opciones.propiedadesConexion();
                    propiedades.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, LIMITE_MS);
                    propiedades.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, LIMITE_MS);
                    admin = Admin.create(propiedades);
                }
            }
        }
        return admin;
    }

    @Override
    public void close() {
        if (admin != null)
            admin.close();
    }
}
