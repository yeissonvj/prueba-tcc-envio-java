package co.tcc.eventos.aplicacion;

import static org.assertj.core.api.Assertions.assertThat;

import co.tcc.eventos.aplicacion.casosuso.ReenviarContingencia;
import co.tcc.eventos.aplicacion.falsos.AlmacenContingenciaEnMemoria;
import co.tcc.eventos.aplicacion.falsos.PublicadorFalso;
import co.tcc.eventos.dominio.EstadoGuia;
import co.tcc.eventos.dominio.EventoGuia;
import java.util.List;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class ReenviarContingenciaPruebas {

    private final PublicadorFalso kafka = new PublicadorFalso();
    private final AlmacenContingenciaEnMemoria contingencia = new AlmacenContingenciaEnMemoria();
    private final ReenviarContingencia casoUso = new ReenviarContingencia(contingencia, kafka);

    private List<EventoGuia> guardarPendientes(int cantidad) {
        var eventos = IntStream.range(0, cantidad)
                .mapToObj(i -> Datos.evento("TCC123", EstadoGuia.RECOGIDA, Datos.EPOCA.plusMinutes(i)))
                .toList();
        eventos.forEach(contingencia::guardar);
        return eventos;
    }

    @Test
    void reenvia_los_pendientes_en_orden_de_llegada_y_los_elimina() {
        var eventos = guardarPendientes(3);

        var reenviados = casoUso.ejecutar(10);

        assertThat(reenviados).isEqualTo(3);
        assertThat(kafka.publicados).containsExactlyElementsOf(eventos);
        assertThat(contingencia.pendientes).isEmpty();
    }

    @Test
    void respeta_el_tamano_del_lote() {
        guardarPendientes(5);

        var reenviados = casoUso.ejecutar(2);

        assertThat(reenviados).isEqualTo(2);
        assertThat(contingencia.pendientes).hasSize(3);
    }

    @Test
    void si_el_broker_sigue_caido_los_pendientes_se_conservan() {
        var eventos = guardarPendientes(2);
        kafka.fallar = true;

        var reenviados = casoUso.ejecutar(10);

        assertThat(reenviados).isZero();
        assertThat(contingencia.pendientes).containsExactlyElementsOf(eventos);
    }
}
