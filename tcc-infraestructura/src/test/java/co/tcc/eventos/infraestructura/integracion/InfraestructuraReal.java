package co.tcc.eventos.infraestructura.integracion;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;
import javax.sql.DataSource;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * PostgreSQL y Kafka reales en contenedores desechables, compartidos por todas las pruebas de integración
 * de la JVM (arrancan una vez). El esquema se crea con los MISMOS archivos de db/migraciones que usa
 * producción y la versión .NET: se prueba el esquema real, no una copia.
 */
public final class InfraestructuraReal {

    private static InfraestructuraReal instancia;

    private final PostgreSQLContainer postgres;
    private final KafkaContainer kafka;
    private final HikariDataSource baseDatos;
    private final JdbcClient jdbc;
    private final TransactionTemplate transaccion;

    private InfraestructuraReal() {
        postgres = new PostgreSQLContainer("postgres:17").withDatabaseName("tcc_eventos");
        kafka = new KafkaContainer("apache/kafka:4.1.2");
        postgres.start();
        kafka.start();

        var configuracion = new HikariConfig();
        configuracion.setJdbcUrl(postgres.getJdbcUrl());
        configuracion.setUsername(postgres.getUsername());
        configuracion.setPassword(postgres.getPassword());
        configuracion.setMaximumPoolSize(60); // la prueba de 50 procesamientos concurrentes
        baseDatos = new HikariDataSource(configuracion);

        Flyway.configure()
                .dataSource(baseDatos)
                .locations("filesystem:" + rutaMigraciones())
                .load()
                .migrate();

        jdbc = JdbcClient.create(baseDatos);
        transaccion = new TransactionTemplate(new DataSourceTransactionManager(baseDatos));
    }

    public static synchronized InfraestructuraReal obtener() {
        if (instancia == null)
            instancia = new InfraestructuraReal();
        return instancia;
    }

    public DataSource baseDatos() {
        return baseDatos;
    }

    public JdbcClient jdbc() {
        return jdbc;
    }

    public TransactionTemplate transaccion() {
        return transaccion;
    }

    public String servidoresKafka() {
        return kafka.getBootstrapServers();
    }

    public String urlJdbc() {
        return postgres.getJdbcUrl();
    }

    public String usuario() {
        return postgres.getUsername();
    }

    public String contrasena() {
        return postgres.getPassword();
    }

    /** Deja las tablas vacías entre pruebas (el esquema se conserva). */
    public void limpiar() {
        jdbc.sql("TRUNCATE guias, historial_eventos, bandeja_salida, contingencia_eventos, notificaciones_enviadas").update();
    }

    public long contar(String sql, Object... parametros) {
        var consulta = jdbc.sql(sql);
        for (var parametro : parametros)
            consulta = consulta.param(parametro);
        return consulta.query(Long.class).single();
    }

    /** Un broker de prueba: una réplica. */
    public String crearTopico(int particiones) {
        var topico = "pruebas." + UUID.randomUUID().toString().replace("-", "");
        try (var admin = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, servidoresKafka()))) {
            admin.createTopics(List.of(new NewTopic(topico, particiones, (short) 1))).all().get();
        } catch (Exception ex) {
            throw new IllegalStateException("No se pudo crear el tópico " + topico, ex);
        }
        return topico;
    }

    /** Lee desde el inicio hasta {@code cantidad} mensajes o hasta que venza el tiempo. */
    public List<ConsumerRecord<String, String>> leer(String topico, int cantidad, Duration tiempoMaximo) {
        return leer(topico, registro -> true, cantidad, tiempoMaximo);
    }

    /** Como {@link #leer(String, int, Duration)}, pero solo cuenta los mensajes con esa clave (p. ej. una guía). */
    public List<ConsumerRecord<String, String>> leerPorClave(String topico, String clave, int cantidad, Duration tiempoMaximo) {
        return leer(topico, registro -> clave.equals(registro.key()), cantidad, tiempoMaximo);
    }

    private List<ConsumerRecord<String, String>> leer(
            String topico, java.util.function.Predicate<ConsumerRecord<String, String>> filtro, int cantidad, Duration tiempoMaximo) {
        var propiedades = new Properties();
        propiedades.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, servidoresKafka());
        propiedades.put(ConsumerConfig.GROUP_ID_CONFIG, "lector-" + UUID.randomUUID());
        propiedades.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        propiedades.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        propiedades.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        var leidos = new ArrayList<ConsumerRecord<String, String>>();
        try (var consumidor = new KafkaConsumer<String, String>(propiedades)) {
            consumidor.subscribe(List.of(topico));
            var limite = System.nanoTime() + tiempoMaximo.toNanos();
            while (leidos.size() < cantidad && System.nanoTime() < limite)
                for (var registro : consumidor.poll(Duration.ofMillis(250)))
                    if (filtro.test(registro))
                        leidos.add(registro);
        }
        return leidos;
    }

    // Maven la pasa como propiedad del sistema; desde el IDE se busca subiendo por las carpetas.
    private static Path rutaMigraciones() {
        var configurada = System.getProperty("migraciones.dir");
        if (configurada != null && Files.isDirectory(Path.of(configurada)))
            return Path.of(configurada).toAbsolutePath();
        for (var carpeta = Path.of("").toAbsolutePath(); carpeta != null; carpeta = carpeta.getParent()) {
            var candidata = carpeta.resolve("db").resolve("migraciones");
            if (Files.isDirectory(candidata))
                return candidata;
        }
        throw new IllegalStateException("No se encontró db/migraciones");
    }
}
