package co.tcc.eventos.notificador;

import static org.assertj.core.api.Assertions.assertThat;

import co.tcc.eventos.contratos.v1.EstadoGuiaCambiadoV1;
import co.tcc.eventos.contratos.v1.EstadosV1;
import co.tcc.eventos.infraestructura.integracion.InfraestructuraReal;
import co.tcc.eventos.infraestructura.kafka.OpcionesKafka;
import co.tcc.eventos.infraestructura.kafka.ProductorKafka;
import co.tcc.eventos.infraestructura.mapeo.JsonContratos;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El notificador completo contra Kafka y PostgreSQL reales, con el SMS caído y esperas de reintento cortas:
 * el cambio recorre la escalera del SMS (1 etapa), pasa al correo y queda registrado una sola vez.
 */
@SpringBootTest
class NotificadorDePuntaAPuntaPruebas {

    private static final InfraestructuraReal INFRA = InfraestructuraReal.obtener();
    private static final String CAMBIADOS = INFRA.crearTopico(3);
    private static final String REINTENTO = INFRA.crearTopico(1);
    private static final String DLQ = INFRA.crearTopico(1);

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", INFRA::urlJdbc);
        registro.add("spring.datasource.username", INFRA::usuario);
        registro.add("spring.datasource.password", INFRA::contrasena);
        registro.add("kafka.servidores", INFRA::servidoresKafka);
        registro.add("kafka.topico-estados-cambiados", () -> CAMBIADOS);
        registro.add("notificador.grupo-consumo", () -> "notificador-" + UUID.randomUUID());
        registro.add("notificador.topico-dlq", () -> DLQ);
        registro.add("notificador.hilos", () -> "3");
        registro.add("notificador.reintentos[0].topico", () -> REINTENTO);
        registro.add("notificador.reintentos[0].espera", () -> "1s");
        registro.add("notificador.etapas-activas", () -> "1");
        registro.add("proveedores.sms.caido", () -> "true");
    }

    @Test
    void con_el_sms_caido_reintenta_y_termina_notificando_por_correo_una_sola_vez() throws InterruptedException {
        var guia = "TCC" + System.nanoTime() + "1";
        var cambio = new EstadoGuiaCambiadoV1(UUID.randomUUID(), guia, EstadosV1.EN_BODEGA_DESTINO, EstadosV1.EN_REPARTO,
                OffsetDateTime.now(), "TMS", null, 4);

        var opciones = new OpcionesKafka();
        opciones.setServidores(INFRA.servidoresKafka());
        try (var productor = new ProductorKafka(opciones)) {
            productor.publicar(CAMBIADOS, guia, JsonContratos.escribir(cambio), Map.of());
            productor.publicar(CAMBIADOS, guia, JsonContratos.escribir(cambio), Map.of()); // repetido: sin doble envío
        }

        assertThat(INFRA.leer(REINTENTO, 1, Duration.ofSeconds(30))).isNotEmpty();

        var limite = System.nanoTime() + Duration.ofSeconds(30).toNanos();
        while (INFRA.contar("SELECT count(*) FROM notificaciones_enviadas WHERE numero_guia = ?", guia) == 0 && System.nanoTime() < limite)
            Thread.sleep(200);

        assertThat(INFRA.contar("SELECT count(*) FROM notificaciones_enviadas WHERE numero_guia = ? AND canal = 'CORREO'", guia)).isEqualTo(1);
        assertThat(INFRA.contar("SELECT count(*) FROM notificaciones_enviadas WHERE numero_guia = ?", guia)).isEqualTo(1);
    }
}
