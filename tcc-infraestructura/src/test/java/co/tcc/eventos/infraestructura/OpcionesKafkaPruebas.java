package co.tcc.eventos.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;

import co.tcc.eventos.infraestructura.kafka.OpcionesKafka;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpcionesKafkaPruebas {

    @Test
    void sin_protocolo_de_seguridad_la_conexion_es_plaintext() {
        var opciones = new OpcionesKafka();
        opciones.setServidores("localhost:29092");

        var propiedades = opciones.propiedadesConexion();

        assertThat(propiedades).containsEntry("bootstrap.servers", "localhost:29092");
        assertThat(propiedades).doesNotContainKey("security.protocol");
    }

    @Test
    void con_certificado_de_cliente_usa_ssl_con_los_tres_archivos(@TempDir Path carpeta) throws IOException {
        var opciones = new OpcionesKafka();
        opciones.setServidores("kafka.aiven:12345");
        opciones.setProtocoloSeguridad("SSL");
        opciones.setCertificadoCa(Files.writeString(carpeta.resolve("ca.pem"), "CA").toString());
        opciones.setCertificadoCliente(Files.writeString(carpeta.resolve("service.cert"), "CERT").toString());
        opciones.setLlaveCliente(Files.writeString(carpeta.resolve("service.key"), "KEY").toString());

        var propiedades = opciones.propiedadesConexion();

        assertThat(propiedades)
                .containsEntry("security.protocol", "SSL")
                .containsEntry("ssl.truststore.type", "PEM")
                .containsEntry("ssl.keystore.certificate.chain", "CERT")
                .containsEntry("ssl.keystore.key", "KEY");
    }

    @Test
    void con_sasl_scram_usa_tls_usuario_y_contrasena() {
        var opciones = new OpcionesKafka();
        opciones.setServidores("kafka.aiven:12345");
        opciones.setProtocoloSeguridad("sasl_ssl");
        opciones.setMecanismoSasl("SCRAM-SHA-256");
        opciones.setUsuarioSasl("avnadmin");
        opciones.setContrasenaSasl("secreto");

        var propiedades = opciones.propiedadesConexion();

        assertThat(propiedades)
                .containsEntry("security.protocol", "SASL_SSL")
                .containsEntry("sasl.mechanism", "SCRAM-SHA-256");
        assertThat((String) propiedades.get("sasl.jaas.config")).contains("username=\"avnadmin\"");
    }

    @Test
    void el_consumidor_confirma_solo_lo_manejado_y_usa_asignacion_cooperativa() {
        var opciones = new OpcionesKafka();
        opciones.setServidores("localhost:29092");

        var propiedades = opciones.propiedadesConsumidor("procesador-estado");

        assertThat(propiedades)
                .containsEntry("group.id", "procesador-estado")
                .containsEntry("enable.auto.commit", false)
                .containsEntry("auto.offset.reset", "earliest");
        assertThat((String) propiedades.get("partition.assignment.strategy")).contains("CooperativeSticky");
    }
}
