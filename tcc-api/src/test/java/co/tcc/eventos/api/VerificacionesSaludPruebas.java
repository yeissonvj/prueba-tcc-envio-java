package co.tcc.eventos.api;

import static org.assertj.core.api.Assertions.assertThat;

import co.tcc.eventos.api.salud.EstadoSalud;
import co.tcc.eventos.api.salud.VerificacionesSalud;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class VerificacionesSaludPruebas {

    @ParameterizedTest
    @CsvSource({
            "true, true, HEALTHY",
            "false, true, DEGRADED",
            "true, false, DEGRADED",
            "false, false, UNHEALTHY"
    })
    void la_api_esta_lista_mientras_pueda_guardar_de_forma_durable_en_algun_lado(
            boolean kafka, boolean contingencia, EstadoSalud esperado) {
        assertThat(VerificacionesSalud.almacenamientoDurable(kafka, contingencia)).isEqualTo(esperado);
    }

    @ParameterizedTest
    @CsvSource({"true, HEALTHY", "false, DEGRADED"})
    void sin_filtro_de_duplicados_la_api_queda_degradada_pero_nunca_fuera(boolean redis, EstadoSalud esperado) {
        assertThat(VerificacionesSalud.filtroDuplicados(redis)).isEqualTo(esperado);
    }
}
