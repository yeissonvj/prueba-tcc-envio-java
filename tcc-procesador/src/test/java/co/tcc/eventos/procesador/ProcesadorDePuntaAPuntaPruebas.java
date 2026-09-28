package co.tcc.eventos.procesador;

import static org.assertj.core.api.Assertions.assertThat;

import co.tcc.eventos.contratos.v1.EstadoGuiaCambiadoV1;
import co.tcc.eventos.infraestructura.integracion.InfraestructuraReal;
import co.tcc.eventos.infraestructura.kafka.OpcionesKafka;
import co.tcc.eventos.infraestructura.kafka.ProductorKafka;
import co.tcc.eventos.infraestructura.mapeo.JsonContratos;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * El procesador completo (Spring, listener, relay) contra Kafka y PostgreSQL reales:
 * un evento publicado queda como estado visible y sale en guias.estados.cambiados; un mensaje ilegible
 * va a la DLQ con su origen sin detener la partición.
 */
@SpringBootTest
class ProcesadorDePuntaAPuntaPruebas {

    private static final InfraestructuraReal INFRA = InfraestructuraReal.obtener();
    private static final String RECIBIDOS = INFRA.crearTopico(3);
    private static final String CAMBIADOS = INFRA.crearTopico(3);
    private static final String DLQ = INFRA.crearTopico(1);

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("spring.datasource.url", INFRA::urlJdbc);
        registro.add("spring.datasource.username", INFRA::usuario);
        registro.add("spring.datasource.password", INFRA::contrasena);
        registro.add("kafka.servidores", INFRA::servidoresKafka);
        registro.add("kafka.topico-eventos-recibidos", () -> RECIBIDOS);
        registro.add("kafka.topico-estados-cambiados", () -> CAMBIADOS);
        registro.add("kafka.topico-eventos-dlq", () -> DLQ);
        registro.add("consumidor.grupo-consumo", () -> "procesador-" + UUID.randomUUID());
        registro.add("consumidor.hilos", () -> "3");
    }

    @Test
    void un_evento_publicado_queda_visible_y_se_anuncia_el_cambio_y_un_ilegible_va_a_la_dlq() {
        var guia = "TCC" + System.nanoTime();
        var evento = """
                {"idEvento":"%s","numeroGuia":"%s","estado":"RECOGIDA","ocurridoEn":"2026-11-30T10:00:00-05:00","origen":"TMS"}"""
                .formatted(UUID.randomUUID(), guia);

        var opciones = new OpcionesKafka();
        opciones.setServidores(INFRA.servidoresKafka());
        try (var productor = new ProductorKafka(opciones)) {
            productor.publicar(RECIBIDOS, "ROTA", "{no es json", Map.of());
            productor.publicar(RECIBIDOS, guia, evento, Map.of());
        }

        var cambios = INFRA.leerPorClave(CAMBIADOS, guia, 1, Duration.ofSeconds(30));
        assertThat(cambios).singleElement().satisfies(m -> {
            var cambio = JsonContratos.leer(m.value(), EstadoGuiaCambiadoV1.class);
            assertThat(m.key()).isEqualTo(guia);
            assertThat(cambio.estadoNuevo()).isEqualTo("RECOGIDA");
            assertThat(cambio.version()).isEqualTo(1);
        });
        assertThat(INFRA.contar("SELECT count(*) FROM guias WHERE numero_guia = ?", guia)).isEqualTo(1);

        var rechazados = INFRA.leerPorClave(DLQ, "ROTA", 1, Duration.ofSeconds(30));
        assertThat(rechazados).singleElement().satisfies(m -> {
            assertThat(m.value()).isEqualTo("{no es json");
            assertThat(new String(m.headers().lastHeader("dlq-topico-origen").value(), StandardCharsets.UTF_8)).isEqualTo(RECIBIDOS);
        });
    }

    /**
     * Lo que en .NET probaba ConsumidorKafkaPruebas: con particiones en paralelo, los eventos de una misma guía
     * se procesan en el orden en que llegaron. Si se desordenaran, alguno quedaría TARDIO o TRANSICION_INVALIDA.
     */
    @Test
    void con_particiones_en_paralelo_cada_guia_conserva_su_orden() throws InterruptedException {
        var recorrido = List.of("CREADA", "RECOGIDA", "EN_BODEGA_ORIGEN", "EN_TRANSITO", "EN_BODEGA_DESTINO");
        var guias = IntStream.range(0, 10).mapToObj(i -> "ORD" + System.nanoTime() + i).toList();
        var hora = OffsetDateTime.of(2026, 11, 30, 8, 0, 0, 0, ZoneOffset.ofHours(-5));

        var opciones = new OpcionesKafka();
        opciones.setServidores(INFRA.servidoresKafka());
        try (var productor = new ProductorKafka(opciones)) {
            for (var paso = 0; paso < recorrido.size(); paso++)      // intercaladas: guía 1 paso 1, guía 2 paso 1...
                for (var guia : guias)
                    productor.publicar(RECIBIDOS, guia, """
                            {"idEvento":"%s","numeroGuia":"%s","estado":"%s","ocurridoEn":"%s","origen":"TMS"}"""
                            .formatted(UUID.randomUUID(), guia, recorrido.get(paso), hora.plusHours(paso)), Map.of());
        }

        var limite = System.nanoTime() + Duration.ofSeconds(40).toNanos();
        while (INFRA.contar("SELECT count(*) FROM historial_eventos WHERE numero_guia IN (%s)".formatted(enLista(guias))) < 50
                && System.nanoTime() < limite)
            Thread.sleep(200);

        assertThat(INFRA.contar("SELECT count(*) FROM historial_eventos WHERE resultado = 'APLICADO' AND numero_guia IN (%s)"
                .formatted(enLista(guias)))).isEqualTo(50);
        assertThat(INFRA.contar("SELECT count(*) FROM guias WHERE version = 5 AND estado_actual = 'EN_BODEGA_DESTINO' AND numero_guia IN (%s)"
                .formatted(enLista(guias)))).isEqualTo(10);
    }

    private static String enLista(List<String> guias) {
        return String.join(",", guias.stream().map(g -> "'" + g + "'").toList());
    }
}
