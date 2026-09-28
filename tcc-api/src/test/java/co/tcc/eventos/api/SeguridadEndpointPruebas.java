package co.tcc.eventos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tcc.eventos.api.soporte.EmisorTokensPruebas;
import co.tcc.eventos.api.soporte.PruebaApi;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@PruebaApi.ApiEnMemoria
class SeguridadEndpointPruebas extends PruebaApi {

    @Test
    void con_token_valido_y_origen_propio_se_acepta() throws Exception {
        mvc.perform(enviar(eventoValido(), EmisorTokensPruebas.token())).andExpect(status().isAccepted());
    }

    @Test
    void sin_token_responde_401() throws Exception {
        mvc.perform(enviar(eventoValido(), null)).andExpect(status().isUnauthorized());
    }

    static Stream<Arguments> tokensInvalidos() {
        return Stream.of(
                Arguments.of("firmado con otra llave", EmisorTokensPruebas.tokenConOtraLlave()),
                Arguments.of("emitido para otra API", EmisorTokensPruebas.tokenConAudiencia("api-facturacion")),
                Arguments.of("de otro emisor", EmisorTokensPruebas.tokenConEmisor("https://emisor-falso/realms/tcc")),
                Arguments.of("vencido", EmisorTokensPruebas.tokenConVigencia(Duration.ofMinutes(-2))),
                Arguments.of("firmado con HS256 (alg confusion)", EmisorTokensPruebas.tokenSimetrico()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("tokensInvalidos")
    void un_token_invalido_responde_401(String caso, String token) throws Exception {
        mvc.perform(enviar(eventoValido(), token)).andExpect(status().isUnauthorized());
    }

    @Test
    void un_token_sin_permiso_de_escritura_responde_403() throws Exception {
        mvc.perform(enviar(eventoValido(), EmisorTokensPruebas.token("portal-consulta", "guias:leer")))
                .andExpect(status().isForbidden());
    }

    @Test
    void un_cliente_no_puede_reportar_eventos_de_otro_sistema() throws Exception {
        var evento = eventoValido("TRANSPORTE");

        mvc.perform(enviar(evento, EmisorTokensPruebas.token("tms", "eventos:escribir"))).andExpect(status().isForbidden());

        assertThat(publicador.publicados).noneMatch(e -> e.idEvento().equals(evento.idEvento()));
    }

    @Test
    void un_cliente_desconocido_no_puede_escribir_aunque_tenga_el_alcance() throws Exception {
        mvc.perform(enviar(eventoValido(), EmisorTokensPruebas.token("sistema-nuevo", "eventos:escribir")))
                .andExpect(status().isForbidden());
    }

    @Test
    void las_sondas_de_salud_no_requieren_token() throws Exception {
        mvc.perform(get("/salud/viva")).andExpect(status().isOk());
    }

    @Test
    void cualquier_otra_ruta_se_niega() throws Exception {
        mvc.perform(get("/api/v1/otra").header("Authorization", "Bearer " + EmisorTokensPruebas.token()))
                .andExpect(status().isForbidden());
    }
}
