package co.tcc.eventos.dominio;

import static co.tcc.eventos.dominio.EstadoGuia.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GuiaPruebas {

    private static final String NUMERO = "TCC123456789";
    private static final OffsetDateTime HORA = OffsetDateTime.of(2026, 11, 30, 10, 0, 0, 0, ZoneOffset.ofHours(-5));

    private static EventoGuia evento(EstadoGuia estado, OffsetDateTime cuando) {
        return new EventoGuia(UUID.randomUUID(), NUMERO, estado, cuando, "TMS");
    }

    @Test
    void aplica_un_cambio_valido_y_sube_la_version() {
        var guia = Guia.crear(evento(EN_BODEGA_DESTINO, HORA));

        var resultado = guia.aplicar(evento(EN_REPARTO, HORA.plusHours(1)));

        assertThat(resultado).isEqualTo(ResultadoAplicacion.APLICADO);
        assertThat(guia.estadoActual()).isEqualTo(EN_REPARTO);
        assertThat(guia.version()).isEqualTo(2);
    }

    @Test
    void un_evento_tardio_no_cambia_el_estado() {
        var guia = Guia.crear(evento(EN_REPARTO, HORA));
        guia.aplicar(evento(ENTREGADA, HORA.plusHours(2)));

        var resultado = guia.aplicar(evento(RECOGIDA, HORA.minusHours(5)));

        assertThat(resultado).isEqualTo(ResultadoAplicacion.TARDIO);
        assertThat(guia.estadoActual()).isEqualTo(ENTREGADA);
    }

    @Test
    void una_transicion_invalida_no_cambia_el_estado() {
        var guia = Guia.crear(evento(CREADA, HORA));

        var resultado = guia.aplicar(evento(ENTREGADA, HORA.plusHours(1)));

        assertThat(resultado).isEqualTo(ResultadoAplicacion.TRANSICION_INVALIDA);
        assertThat(guia.estadoActual()).isEqualTo(CREADA);
        assertThat(guia.version()).isEqualTo(1);
    }

    @Test
    void una_guia_entregada_no_acepta_mas_cambios() {
        var guia = Guia.crear(evento(EN_REPARTO, HORA));
        guia.aplicar(evento(ENTREGADA, HORA.plusHours(1)));

        var resultado = guia.aplicar(evento(NOVEDAD, HORA.plusHours(2)));

        assertThat(resultado).isEqualTo(ResultadoAplicacion.TRANSICION_INVALIDA);
    }

    @Test
    void rechaza_eventos_de_otra_guia() {
        var guia = Guia.crear(evento(CREADA, HORA));
        var eventoAjeno = new EventoGuia(UUID.randomUUID(), "OTRA", RECOGIDA, HORA, "TMS");

        assertThatThrownBy(() -> guia.aplicar(eventoAjeno)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void la_misma_hora_no_es_tardia_aunque_llegue_con_otro_huso_horario() {
        var guia = Guia.crear(evento(EN_BODEGA_DESTINO, HORA));

        // 15:00Z es el mismo instante que 10:00-05:00: no es anterior, así que se evalúa la transición.
        var resultado = guia.aplicar(evento(EN_REPARTO, HORA.withOffsetSameInstant(ZoneOffset.UTC)));

        assertThat(resultado).isEqualTo(ResultadoAplicacion.APLICADO);
    }
}
