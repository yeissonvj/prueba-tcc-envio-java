package co.tcc.eventos.aplicacion;

import static co.tcc.eventos.aplicacion.Datos.nuevoEvento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tcc.eventos.aplicacion.decoradores.PublicadorConContingencia;
import co.tcc.eventos.aplicacion.falsos.AlmacenContingenciaEnMemoria;
import co.tcc.eventos.aplicacion.falsos.PublicadorFalso;
import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import org.junit.jupiter.api.Test;

class PublicadorConContingenciaPruebas {

    private final PublicadorFalso kafka = new PublicadorFalso();
    private final AlmacenContingenciaEnMemoria contingencia = new AlmacenContingenciaEnMemoria();
    private final PublicadorConContingencia publicador = new PublicadorConContingencia(kafka, contingencia);

    @Test
    void con_el_broker_disponible_no_se_usa_la_contingencia() {
        publicador.publicar(nuevoEvento());

        assertThat(kafka.publicados).hasSize(1);
        assertThat(contingencia.pendientes).isEmpty();
    }

    @Test
    void si_el_broker_falla_el_evento_queda_en_contingencia_sin_error() {
        kafka.fallar = true;
        var evento = nuevoEvento();

        publicador.publicar(evento);

        assertThat(contingencia.pendientes).containsExactly(evento);
    }

    @Test
    void si_fallan_broker_y_contingencia_se_informa_que_no_es_durable() {
        kafka.fallar = true;
        contingencia.fallar = true;

        assertThatThrownBy(() -> publicador.publicar(nuevoEvento())).isInstanceOf(PublicacionFallidaException.class);
    }
}
