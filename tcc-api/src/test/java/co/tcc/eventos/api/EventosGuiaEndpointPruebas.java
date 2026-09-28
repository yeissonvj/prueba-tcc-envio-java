package co.tcc.eventos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tcc.eventos.api.soporte.EmisorTokensPruebas;
import co.tcc.eventos.api.soporte.PruebaApi;
import co.tcc.eventos.contratos.v1.EventoGuiaV1;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

@PruebaApi.ApiEnMemoria
class EventosGuiaEndpointPruebas extends PruebaApi {

    private final String tms = EmisorTokensPruebas.token();

    @Test
    void un_evento_valido_responde_202_con_ubicacion_y_se_publica() throws Exception {
        var evento = eventoValido();

        mvc.perform(enviar(evento, tms))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/guias/" + evento.numeroGuia()))
                .andExpect(jsonPath("$.idEvento").value(evento.idEvento().toString()))
                .andExpect(jsonPath("$.resultado").value("ACEPTADO"));

        assertThat(publicador.publicados).anyMatch(e -> e.idEvento().equals(evento.idEvento()));
    }

    @Test
    void un_evento_repetido_responde_200_duplicado_y_no_se_publica_otra_vez() throws Exception {
        var evento = eventoValido();
        mvc.perform(enviar(evento, tms)).andExpect(status().isAccepted());

        mvc.perform(enviar(evento, tms))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultado").value("DUPLICADO"));

        assertThat(publicador.publicados).filteredOn(e -> e.idEvento().equals(evento.idEvento())).hasSize(1);
    }

    @Test
    void un_evento_que_no_cumple_el_contrato_responde_400_con_los_campos_en_error() throws Exception {
        var valido = eventoValido();
        var invalido = new EventoGuiaV1(valido.idEvento(), "TCC-1", "PERDIDA", valido.ocurridoEn(), "TMS", null);

        mvc.perform(enviar(invalido, tms))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.errors.numeroGuia").exists())
                .andExpect(jsonPath("$.errors.estado").exists());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{ \"idEvento\":", "{ \"idEvento\": 123 }"})
    void un_json_mal_formado_responde_400_y_no_500(String cuerpo) throws Exception {
        mvc.perform(enviarTexto(cuerpo, tms))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void si_nada_es_durable_responde_503_con_retry_after_sin_detalles_internos() throws Exception {
        publicador.fallar = true;

        mvc.perform(enviar(eventoValido(), tms))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "5"))
                .andExpect(content().string(not(containsString("Kafka"))))
                .andExpect(content().string(not(containsString("Broker"))));
    }

    @Test
    void una_novedad_demasiado_larga_se_rechaza_con_400() throws Exception {
        var base = eventoValido();
        var evento = new EventoGuiaV1(base.idEvento(), base.numeroGuia(), "NOVEDAD", base.ocurridoEn(), "TMS", "x".repeat(5_000));

        mvc.perform(enviar(evento, tms)).andExpect(status().isBadRequest());

        assertThat(publicador.publicados).noneMatch(e -> e.idEvento().equals(evento.idEvento()));
    }

    @Test
    void un_cuerpo_de_mas_de_64_kb_responde_413_sin_leerlo() throws Exception {
        var gigante = "{\"relleno\":\"" + "x".repeat(70_000) + "\"}";

        mvc.perform(enviarTexto(gigante, tms))
                .andExpect(status().isContentTooLarge())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void conserva_el_huso_horario_del_emisor_en_el_evento_publicado() throws Exception {
        var cuerpo = """
                {"idEvento":"%s","numeroGuia":"TCC77","estado":"CREADA","ocurridoEn":"2020-11-30T10:15:00-05:00","origen":"TMS"}"""
                .formatted(java.util.UUID.randomUUID());

        mvc.perform(enviarTexto(cuerpo, tms)).andExpect(status().isAccepted());

        assertThat(publicador.publicados).filteredOn(e -> e.numeroGuia().equals("TCC77"))
                .singleElement()
                .satisfies(e -> assertThat(e.ocurridoEn().getOffset().getTotalSeconds()).isEqualTo(-5 * 3600));
    }
}
