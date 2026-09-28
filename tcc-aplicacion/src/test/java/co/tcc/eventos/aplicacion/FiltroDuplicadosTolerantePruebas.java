package co.tcc.eventos.aplicacion;

import static co.tcc.eventos.aplicacion.Datos.nuevoEvento;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import co.tcc.eventos.aplicacion.casosuso.RecibirEvento;
import co.tcc.eventos.aplicacion.casosuso.ResultadoRecepcion;
import co.tcc.eventos.aplicacion.decoradores.FiltroDuplicadosTolerante;
import co.tcc.eventos.aplicacion.falsos.FiltroDuplicadosQueFalla;
import co.tcc.eventos.aplicacion.falsos.PublicadorFalso;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FiltroDuplicadosTolerantePruebas {

    private final FiltroDuplicadosTolerante filtro = new FiltroDuplicadosTolerante(new FiltroDuplicadosQueFalla());

    @Test
    void si_el_filtro_falla_el_evento_se_considera_nuevo() {
        assertThat(filtro.yaRecibido(UUID.randomUUID())).isFalse();
    }

    @Test
    void si_no_se_puede_marcar_no_se_propaga_el_error() {
        assertThatCode(() -> filtro.marcarRecibido(UUID.randomUUID())).doesNotThrowAnyException();
    }

    @Test
    void con_el_filtro_caido_la_recepcion_publica_igual() {
        var publicador = new PublicadorFalso();
        var casoUso = new RecibirEvento(publicador, filtro);

        var resultado = casoUso.ejecutar(nuevoEvento());

        assertThat(resultado).isEqualTo(ResultadoRecepcion.ACEPTADO);
        assertThat(publicador.publicados).hasSize(1);
    }
}
