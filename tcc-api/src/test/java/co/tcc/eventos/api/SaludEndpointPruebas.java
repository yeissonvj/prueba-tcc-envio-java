package co.tcc.eventos.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import co.tcc.eventos.api.soporte.PruebaApi;
import org.junit.jupiter.api.Test;

@PruebaApi.ApiEnMemoria
class SaludEndpointPruebas extends PruebaApi {

    @Test
    void la_sonda_de_vida_no_depende_de_servicios_externos() throws Exception {
        sondaKafka.disponible = false;
        sondaContingencia.disponible = false;
        sondaFiltro.disponible = false;

        mvc.perform(get("/salud/viva")).andExpect(status().isOk()).andExpect(content().string("Healthy"));
    }

    @Test
    void con_todo_disponible_esta_lista() throws Exception {
        mvc.perform(get("/salud/lista")).andExpect(status().isOk()).andExpect(content().string("Healthy"));
    }

    @Test
    void con_kafka_caido_y_contingencia_disponible_sigue_recibiendo_trafico() throws Exception {
        sondaKafka.disponible = false;

        mvc.perform(get("/salud/lista")).andExpect(status().isOk()).andExpect(content().string("Degraded"));
    }

    @Test
    void sin_almacenamiento_durable_sale_del_balanceador_sin_revelar_que_fallo() throws Exception {
        sondaKafka.disponible = false;
        sondaContingencia.disponible = false;

        mvc.perform(get("/salud/lista"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(content().string("Unhealthy"));
    }
}
