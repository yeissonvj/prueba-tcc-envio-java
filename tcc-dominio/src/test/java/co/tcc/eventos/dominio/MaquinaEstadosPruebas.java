package co.tcc.eventos.dominio;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class MaquinaEstadosPruebas {

    @ParameterizedTest
    @CsvSource({
            "CREADA, RECOGIDA",
            "EN_TRANSITO, EN_BODEGA_DESTINO",
            "EN_REPARTO, ENTREGADA",
            "EN_REPARTO, NOVEDAD",
            "NOVEDAD, REINTENTO_ENTREGA",
            "REINTENTO_ENTREGA, EN_REPARTO"
    })
    void permite_transiciones_validas(EstadoGuia desde, EstadoGuia hacia) {
        assertThat(MaquinaEstados.puedeTransitar(desde, hacia)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({
            "CREADA, ENTREGADA",
            "ENTREGADA, EN_TRANSITO",
            "DEVUELTA, EN_REPARTO",
            "EN_REPARTO, EN_REPARTO"
    })
    void rechaza_transiciones_invalidas(EstadoGuia desde, EstadoGuia hacia) {
        assertThat(MaquinaEstados.puedeTransitar(desde, hacia)).isFalse();
    }

    @ParameterizedTest
    @EnumSource(names = {"ENTREGADA", "DEVUELTA"})
    void entregada_y_devuelta_son_estados_finales(EstadoGuia estado) {
        assertThat(MaquinaEstados.esEstadoFinal(estado)).isTrue();
    }

    @Test
    void todos_los_estados_tienen_reglas_definidas() {
        for (var estado : EstadoGuia.values())
            assertThatCode(() -> MaquinaEstados.puedeTransitar(estado, EstadoGuia.CREADA)).doesNotThrowAnyException();
    }
}
