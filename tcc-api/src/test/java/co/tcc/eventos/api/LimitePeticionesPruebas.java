package co.tcc.eventos.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tcc.eventos.api.soporte.EmisorTokensPruebas;
import co.tcc.eventos.api.soporte.PruebaApi;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;

@PruebaApi.ApiEnMemoria
@TestPropertySource(properties = {
        "seguridad.rafaga-por-cliente=3",
        "seguridad.peticiones-por-segundo-por-cliente=1"
})
class LimitePeticionesPruebas extends PruebaApi {

    @Test
    void al_superar_el_limite_responde_429_con_retry_after_sin_afectar_a_otros_clientes() throws Exception {
        var tms = EmisorTokensPruebas.token("tms", "eventos:escribir");
        var transporte = EmisorTokensPruebas.token("transporte", "eventos:escribir");

        MvcResult ultima = null;
        for (var i = 0; i < 5; i++)
            ultima = mvc.perform(enviar(eventoValido("TMS"), tms)).andReturn();
        var otroCliente = mvc.perform(enviar(eventoValido("TRANSPORTE"), transporte));

        assertThat(ultima.getResponse().getStatus()).isEqualTo(429);
        assertThat(ultima.getResponse().getHeader("Retry-After")).isNotBlank();
        otroCliente.andExpect(status().isAccepted());
    }
}
