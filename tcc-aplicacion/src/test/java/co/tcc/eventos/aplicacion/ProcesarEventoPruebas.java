package co.tcc.eventos.aplicacion;

import static co.tcc.eventos.aplicacion.Datos.HORA;
import static co.tcc.eventos.dominio.EstadoGuia.*;
import static org.assertj.core.api.Assertions.assertThat;

import co.tcc.eventos.aplicacion.casosuso.ProcesarEvento;
import co.tcc.eventos.aplicacion.casosuso.ResultadoProcesamiento;
import co.tcc.eventos.aplicacion.falsos.RepositorioGuiasEnMemoria;
import co.tcc.eventos.aplicacion.falsos.RepositorioGuiasEnMemoria.Cambio;
import co.tcc.eventos.dominio.EstadoGuia;
import co.tcc.eventos.dominio.EventoGuia;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ProcesarEventoPruebas {

    private static final String NUMERO = "TCC123";

    private final RepositorioGuiasEnMemoria repositorio = new RepositorioGuiasEnMemoria();
    private final ProcesarEvento casoUso = new ProcesarEvento(repositorio);

    private static EventoGuia evento(EstadoGuia estado, OffsetDateTime cuando) {
        return Datos.evento(NUMERO, estado, cuando);
    }

    @Test
    void el_primer_evento_crea_la_guia() {
        var resultado = casoUso.ejecutar(evento(RECOGIDA, HORA));

        assertThat(resultado).isEqualTo(ResultadoProcesamiento.APLICADO);
        assertThat(repositorio.guias.get(NUMERO).estadoActual()).isEqualTo(RECOGIDA);
    }

    @Test
    void un_evento_ya_procesado_se_reconoce_como_duplicado() {
        var evento = evento(RECOGIDA, HORA);
        casoUso.ejecutar(evento);

        var resultado = casoUso.ejecutar(evento);

        assertThat(resultado).isEqualTo(ResultadoProcesamiento.DUPLICADO);
        assertThat(repositorio.historial).hasSize(1);
    }

    @Test
    void un_evento_tardio_queda_en_historial_sin_cambiar_el_estado() {
        casoUso.ejecutar(evento(EN_REPARTO, HORA));

        var resultado = casoUso.ejecutar(evento(RECOGIDA, HORA.minusHours(3)));

        assertThat(resultado).isEqualTo(ResultadoProcesamiento.TARDIO);
        assertThat(repositorio.historial).hasSize(2);
        assertThat(repositorio.guias.get(NUMERO).estadoActual()).isEqualTo(EN_REPARTO);
    }

    @Test
    void una_transicion_invalida_queda_en_historial_sin_publicar_cambio() {
        casoUso.ejecutar(evento(CREADA, HORA));

        var resultado = casoUso.ejecutar(evento(ENTREGADA, HORA.plusHours(1)));

        assertThat(resultado).isEqualTo(ResultadoProcesamiento.TRANSICION_INVALIDA);
        assertThat(repositorio.historial).hasSize(2);
        assertThat(repositorio.cambios).hasSize(1);
    }

    @Test
    void cada_cambio_aplicado_lleva_el_estado_anterior_y_una_version_consecutiva() {
        casoUso.ejecutar(evento(EN_BODEGA_DESTINO, HORA));
        casoUso.ejecutar(evento(EN_REPARTO, HORA.plusHours(1)));
        casoUso.ejecutar(evento(ENTREGADA, HORA.plusHours(2)));

        assertThat(repositorio.cambios).containsExactly(
                new Cambio(null, EN_BODEGA_DESTINO, 1),
                new Cambio(EN_BODEGA_DESTINO, EN_REPARTO, 2),
                new Cambio(EN_REPARTO, ENTREGADA, 3));
    }
}
