package co.tcc.eventos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tcc.eventos.api.soporte.EmisorTokensPruebas;
import co.tcc.eventos.api.soporte.PruebaApi;
import co.tcc.eventos.contratos.v1.EventoHistorialV1;
import co.tcc.eventos.contratos.v1.GuiaV1;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

@PruebaApi.ApiEnMemoria
class GuiasEndpointPruebas extends PruebaApi {

    private static final OffsetDateTime HORA = OffsetDateTime.of(2026, 9, 26, 10, 15, 0, 0, ZoneOffset.ofHours(-5));

    private final String portal = EmisorTokensPruebas.token("portal-consulta", "guias:leer");

    private GuiaV1 registrarGuia(String numero) {
        var guia = new GuiaV1(numero, "EN_REPARTO", HORA, 2, List.of(
                new EventoHistorialV1(UUID.randomUUID(), "EN_REPARTO", HORA, "TRANSPORTE", null, "APLICADO"),
                new EventoHistorialV1(UUID.randomUUID(), "EN_BODEGA_DESTINO", HORA.minusHours(2), "TMS", null, "APLICADO")));
        consultaGuias.guias.put(numero, guia);
        return guia;
    }

    @Test
    void con_alcance_de_lectura_devuelve_estado_e_historial_sin_cache() throws Exception {
        var guia = registrarGuia("TCC700000001");

        mvc.perform(consultar("TCC700000001", portal))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.estadoActual").value("EN_REPARTO"))
                .andExpect(jsonPath("$.ultimoEventoEn").value("2026-09-26T10:15:00-05:00"))
                .andExpect(jsonPath("$.historial[0].idEvento").value(guia.historial().get(0).idEvento().toString()))
                .andExpect(jsonPath("$.historial[1].idEvento").value(guia.historial().get(1).idEvento().toString()));
    }

    @Test
    void una_guia_sin_eventos_procesados_responde_404() throws Exception {
        mvc.perform(consultar("TCC799999999", portal))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    void un_numero_mal_formado_responde_400_sin_consultar_la_base() throws Exception {
        var consultasAntes = consultaGuias.consultas.get();

        mvc.perform(get("/api/v1/guias/TCC' OR 1=1").header("Authorization", "Bearer " + portal))
                .andExpect(status().isBadRequest());

        assertThat(consultaGuias.consultas.get()).isEqualTo(consultasAntes);
    }

    @Test
    void un_token_de_solo_escritura_no_puede_consultar() throws Exception {
        registrarGuia("TCC700000002");

        mvc.perform(consultar("TCC700000002", EmisorTokensPruebas.token())).andExpect(status().isForbidden());
    }

    @Test
    void sin_token_responde_401() throws Exception {
        mvc.perform(consultar("TCC1", null)).andExpect(status().isUnauthorized());
    }
}
