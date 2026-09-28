package co.tcc.eventos.infraestructura.kafka;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.CooperativeStickyAssignor;
import org.apache.kafka.common.config.SaslConfigs;
import org.apache.kafka.common.config.SslConfigs;
import org.apache.kafka.common.serialization.StringDeserializer;

/**
 * Opciones de Kafka (se enlazan desde la configuración "kafka.*" o variables KAFKA_*).
 * Es el único lugar donde se decide cómo se conecta CUALQUIER cliente de Kafka del sistema
 * (productor, consumidores, sonda de salud): nadie se conecta con otras reglas por accidente.
 */
public class OpcionesKafka {

    private String servidores = "";
    private String topicoEventosRecibidos = "guias.eventos.recibidos";
    private String topicoEstadosCambiados = "guias.estados.cambiados";
    private String topicoEventosDlq = "guias.eventos.dlq";
    private int tiempoMaximoEntregaMs = 5000;

    // Debe coincidir con min.insync.replicas del tópico: por debajo, acks=all rechaza escrituras.
    private int replicasMinimasSincronizadas = 2;

    // Circuito: se abre si en la ventana falla al menos la mitad de un mínimo de envíos.
    private int circuitoMinimoEnvios = 10;
    private int circuitoVentanaSegundos = 10;
    private int circuitoSegundosAbierto = 15;

    // ---- Conexión segura (Kafka administrado, p. ej. Aiven). Sin protocoloSeguridad = PLAINTEXT (local) ----

    /** SSL (certificado de cliente) o SASL_SSL (usuario y contraseña SCRAM sobre TLS). */
    private String protocoloSeguridad;
    /** Ruta al certificado de la autoridad que firmó los brokers (ca.pem). */
    private String certificadoCa;
    /** Solo con SSL: certificado y llave del cliente en PEM (service.cert, service.key). */
    private String certificadoCliente;
    private String llaveCliente;
    /** Solo con SASL_SSL: SCRAM-SHA-256 o SCRAM-SHA-512. La contraseña llega por variable de entorno. */
    private String mecanismoSasl;
    private String usuarioSasl;
    private String contrasenaSasl;

    /** Propiedades de conexión comunes a productor, consumidores y administración. */
    public Map<String, Object> propiedadesConexion() {
        if (vacio(servidores))
            throw new IllegalStateException("Falta la configuración 'kafka.servidores'.");

        var propiedades = new HashMap<String, Object>();
        propiedades.put(CommonClientConfigs.BOOTSTRAP_SERVERS_CONFIG, servidores);
        if (vacio(protocoloSeguridad))
            return propiedades;

        propiedades.put(CommonClientConfigs.SECURITY_PROTOCOL_CONFIG, protocoloSeguridad.toUpperCase());
        if (!vacio(certificadoCa)) {
            propiedades.put(SslConfigs.SSL_TRUSTSTORE_TYPE_CONFIG, "PEM");
            propiedades.put(SslConfigs.SSL_TRUSTSTORE_LOCATION_CONFIG, certificadoCa);
        }
        if (!vacio(certificadoCliente) && !vacio(llaveCliente)) {
            // Kafka en Java lee certificado + llave PEM desde un solo archivo o desde el contenido;
            // se pasan como contenido para aceptar los dos archivos que entrega Aiven.
            propiedades.put(SslConfigs.SSL_KEYSTORE_TYPE_CONFIG, "PEM");
            propiedades.put(SslConfigs.SSL_KEYSTORE_CERTIFICATE_CHAIN_CONFIG, leerArchivo(certificadoCliente));
            propiedades.put(SslConfigs.SSL_KEYSTORE_KEY_CONFIG, leerArchivo(llaveCliente));
        }
        if (!vacio(mecanismoSasl)) {
            propiedades.put(SaslConfigs.SASL_MECHANISM, mecanismoSasl.toUpperCase());
            propiedades.put(SaslConfigs.SASL_JAAS_CONFIG,
                    "org.apache.kafka.common.security.scram.ScramLoginModule required username=\""
                            + usuarioSasl + "\" password=\"" + contrasenaSasl + "\";");
        }
        return propiedades;
    }

    /**
     * Consumidor durable: sin commit automático (el contenedor confirma solo lo ya manejado),
     * lectura desde el inicio si el grupo es nuevo, asignación cooperative-sticky y tolerancia a
     * reintentos bloqueantes largos antes de ceder la partición.
     */
    public Map<String, Object> propiedadesConsumidor(String grupo) {
        var propiedades = propiedadesConexion();
        propiedades.put(ConsumerConfig.GROUP_ID_CONFIG, grupo);
        propiedades.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        propiedades.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        propiedades.put(ConsumerConfig.PARTITION_ASSIGNMENT_STRATEGY_CONFIG, CooperativeStickyAssignor.class.getName());
        propiedades.put(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, 600_000);
        propiedades.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        propiedades.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return propiedades;
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }

    private static String leerArchivo(String ruta) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(ruta));
        } catch (java.io.IOException ex) {
            throw new IllegalStateException("No se pudo leer el archivo de certificado " + ruta, ex);
        }
    }

    // ---- getters y setters (enlace de configuración) ----

    public String getServidores() { return servidores; }
    public void setServidores(String servidores) { this.servidores = servidores; }
    public String getTopicoEventosRecibidos() { return topicoEventosRecibidos; }
    public void setTopicoEventosRecibidos(String topico) { this.topicoEventosRecibidos = topico; }
    public String getTopicoEstadosCambiados() { return topicoEstadosCambiados; }
    public void setTopicoEstadosCambiados(String topico) { this.topicoEstadosCambiados = topico; }
    public String getTopicoEventosDlq() { return topicoEventosDlq; }
    public void setTopicoEventosDlq(String topico) { this.topicoEventosDlq = topico; }
    public int getTiempoMaximoEntregaMs() { return tiempoMaximoEntregaMs; }
    public void setTiempoMaximoEntregaMs(int ms) { this.tiempoMaximoEntregaMs = ms; }
    public int getReplicasMinimasSincronizadas() { return replicasMinimasSincronizadas; }
    public void setReplicasMinimasSincronizadas(int replicas) { this.replicasMinimasSincronizadas = replicas; }
    public int getCircuitoMinimoEnvios() { return circuitoMinimoEnvios; }
    public void setCircuitoMinimoEnvios(int envios) { this.circuitoMinimoEnvios = envios; }
    public int getCircuitoVentanaSegundos() { return circuitoVentanaSegundos; }
    public void setCircuitoVentanaSegundos(int segundos) { this.circuitoVentanaSegundos = segundos; }
    public int getCircuitoSegundosAbierto() { return circuitoSegundosAbierto; }
    public void setCircuitoSegundosAbierto(int segundos) { this.circuitoSegundosAbierto = segundos; }
    public String getProtocoloSeguridad() { return protocoloSeguridad; }
    public void setProtocoloSeguridad(String protocolo) { this.protocoloSeguridad = protocolo; }
    public String getCertificadoCa() { return certificadoCa; }
    public void setCertificadoCa(String ruta) { this.certificadoCa = ruta; }
    public String getCertificadoCliente() { return certificadoCliente; }
    public void setCertificadoCliente(String ruta) { this.certificadoCliente = ruta; }
    public String getLlaveCliente() { return llaveCliente; }
    public void setLlaveCliente(String ruta) { this.llaveCliente = ruta; }
    public String getMecanismoSasl() { return mecanismoSasl; }
    public void setMecanismoSasl(String mecanismo) { this.mecanismoSasl = mecanismo; }
    public String getUsuarioSasl() { return usuarioSasl; }
    public void setUsuarioSasl(String usuario) { this.usuarioSasl = usuario; }
    public String getContrasenaSasl() { return contrasenaSasl; }
    public void setContrasenaSasl(String contrasena) { this.contrasenaSasl = contrasena; }
}
