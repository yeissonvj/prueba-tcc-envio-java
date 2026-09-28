package co.tcc.eventos.aplicacion;

import static co.tcc.eventos.aplicacion.Datos.nuevoEvento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tcc.eventos.aplicacion.casosuso.RecibirEvento;
import co.tcc.eventos.aplicacion.casosuso.ResultadoRecepcion;
import co.tcc.eventos.aplicacion.falsos.FiltroDuplicadosEnMemoria;
import co.tcc.eventos.aplicacion.falsos.PublicadorFalso;
import co.tcc.eventos.aplicacion.puertos.PublicacionFallidaException;
import org.junit.jupiter.api.Test;

class RecibirEventoPruebas {

    private final PublicadorFalso publicador = new PublicadorFalso();
    private final RecibirEvento casoUso = new RecibirEvento(publicador, new FiltroDuplicadosEnMemoria());

    @Test
    void un_evento_nuevo_se_publica_y_se_acepta() {
        var resultado = casoUso.ejecutar(nuevoEvento());

        assertThat(resultado).isEqualTo(ResultadoRecepcion.ACEPTADO);
        assertThat(publicador.publicados).hasSize(1);
    }

    @Test
    void un_evento_repetido_no_se_publica_dos_veces() {
        var evento = nuevoEvento();
        casoUso.ejecutar(evento);

        var resultado = casoUso.ejecutar(evento);

        assertThat(resultado).isEqualTo(ResultadoRecepcion.DUPLICADO);
        assertThat(publicador.publicados).hasSize(1);
    }

    @Test
    void si_la_publicacion_falla_el_reintento_no_se_descarta() {
        var evento = nuevoEvento();
        publicador.fallar = true;
        assertThatThrownBy(() -> casoUso.ejecutar(evento)).isInstanceOf(PublicacionFallidaException.class);

        publicador.fallar = false;
        var resultado = casoUso.ejecutar(evento);

        assertThat(resultado).isEqualTo(ResultadoRecepcion.ACEPTADO);
        assertThat(publicador.publicados).hasSize(1);
    }
}
