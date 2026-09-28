package co.tcc.eventos.infraestructura.integracion;

import static co.tcc.eventos.dominio.EstadoGuia.*;
import static org.assertj.core.api.Assertions.assertThat;

import co.tcc.eventos.aplicacion.Datos;
import co.tcc.eventos.aplicacion.casosuso.ProcesarEvento;
import co.tcc.eventos.contratos.v1.EstadoGuiaCambiadoV1;
import co.tcc.eventos.dominio.EstadoGuia;
import co.tcc.eventos.infraestructura.kafka.OpcionesKafka;
import co.tcc.eventos.infraestructura.kafka.ProductorKafka;
import co.tcc.eventos.infraestructura.mapeo.JsonContratos;
import co.tcc.eventos.infraestructura.postgres.RelayBandejaSalida;
import co.tcc.eventos.infraestructura.postgres.RepositorioGuiasJdbc;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RelayBandejaSalidaPruebas {

    private final InfraestructuraReal infra = InfraestructuraReal.obtener();

    @BeforeEach
    void limpiar() {
        infra.limpiar();
    }

    @Test
    void dos_relays_en_paralelo_publican_cada_cambio_una_sola_vez_y_en_orden() {
        var topico = infra.crearTopico(3);
        var opciones = new OpcionesKafka();
        opciones.setServidores(infra.servidoresKafka());
        opciones.setTopicoEstadosCambiados(topico);
        opciones.setReplicasMinimasSincronizadas(1);

        var procesar = new ProcesarEvento(new RepositorioGuiasJdbc(infra.jdbc(), infra.transaccion()));
        var recorrido = List.of(CREADA, RECOGIDA, EN_BODEGA_ORIGEN, EN_TRANSITO, EN_BODEGA_DESTINO);
        for (var i = 0; i < recorrido.size(); i++)
            procesar.ejecutar(Datos.evento("TCC20", recorrido.get(i), Datos.HORA.plusHours(i)));

        try (var productor = new ProductorKafka(opciones)) {
            var relayA = new RelayBandejaSalida(infra.jdbc(), infra.transaccion(), productor, opciones);
            var relayB = new RelayBandejaSalida(infra.jdbc(), infra.transaccion(), productor, opciones);

            var a = CompletableFuture.supplyAsync(() -> relayA.publicarPendientes(100));
            var b = CompletableFuture.supplyAsync(() -> relayB.publicarPendientes(100));

            assertThat(a.join() + b.join()).isEqualTo(recorrido.size()); // el candado evita que ambos publiquen lo mismo
        }
        assertThat(infra.contar("SELECT count(*) FROM bandeja_salida")).isZero();

        var mensajes = infra.leer(topico, recorrido.size(), Duration.ofSeconds(20));
        assertThat(mensajes).extracting(m -> JsonContratos.leer(m.value(), EstadoGuiaCambiadoV1.class).version())
                .containsExactly(1L, 2L, 3L, 4L, 5L);
        assertThat(mensajes).allSatisfy(m -> assertThat(m.key()).isEqualTo("TCC20"));
        assertThat(mensajes).extracting(m -> EstadoGuia.valueOf(JsonContratos.leer(m.value(), EstadoGuiaCambiadoV1.class).estadoNuevo()))
                .containsExactlyElementsOf(recorrido);
    }
}
