package co.tcc.eventos.notificador;

import static org.assertj.core.api.Assertions.assertThat;

import co.tcc.eventos.infraestructura.kafka.MensajeKafka;
import co.tcc.eventos.notificador.enrutamiento.EnrutadorNotificacionesKafka;
import co.tcc.eventos.notificador.enrutamiento.OpcionesNotificador.EtapaReintento;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OpcionesNotificadorPruebas {

    @Test
    void sin_limite_se_usan_todas_las_etapas() {
        assertThat(ManejadorNotificacionPruebas.opciones().conEtapasActivas().getReintentos()).hasSize(3);
    }

    @Test
    void con_limite_se_usan_solo_las_primeras() {
        var opciones = ManejadorNotificacionPruebas.opciones();
        opciones.setEtapasActivas(1);
        opciones.setTopicoDlq("dlq");

        var recortadas = opciones.conEtapasActivas();

        assertThat(recortadas.getReintentos()).extracting(EtapaReintento::getTopico).containsExactly("r1");
        assertThat(recortadas.getTopicoDlq()).isEqualTo("dlq");
    }

    @Test
    void lee_el_vencimiento_que_escribe_la_version_dotnet() {
        var mensaje = new MensajeKafka("r", 0, 1, "TCC1", "{}",
                Map.of(EnrutadorNotificacionesKafka.ENCABEZADO_REINTENTAR_DESPUES, "2026-11-30T15:16:00.1234567+00:00"), null);

        assertThat(EnrutadorNotificacionesKafka.vencimiento(mensaje))
                .isAtSameInstantAs(OffsetDateTime.of(2026, 11, 30, 15, 16, 0, 123_456_700, ZoneOffset.UTC));
    }

    @Test
    void sin_marca_de_vencimiento_se_procesa_de_inmediato() {
        assertThat(EnrutadorNotificacionesKafka.vencimiento(new MensajeKafka("r", 0, 1, "TCC1", "{}"))).isNull();
    }
}
