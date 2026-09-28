package co.tcc.eventos.dominio.notificaciones;

import static org.assertj.core.api.Assertions.assertThat;

import co.tcc.eventos.dominio.EstadoGuia;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PoliticaNotificacionPruebas {

    private static final OffsetDateTime EPOCA = OffsetDateTime.of(1970, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC);

    @ParameterizedTest
    @CsvSource({
            "RECOGIDA, true",
            "EN_REPARTO, true",
            "ENTREGADA, true",
            "NOVEDAD, true",
            "DEVUELTA, true",
            "EN_BODEGA_ORIGEN, false",
            "EN_TRANSITO, false"
    })
    void solo_notifica_lo_que_le_importa_al_cliente(EstadoGuia estado, boolean esperado) {
        assertThat(PoliticaNotificacion.debeNotificar(estado)).isEqualTo(esperado);
    }

    @Test
    void todo_estado_que_notifica_tiene_texto() {
        Arrays.stream(EstadoGuia.values())
                .filter(PoliticaNotificacion::debeNotificar)
                .forEach(estado -> assertThat(PoliticaNotificacion.texto(
                        new CambioEstadoGuia(UUID.randomUUID(), "TCC123", null, estado, EPOCA, 1)))
                        .contains("TCC123"));
    }

    @Test
    void la_clave_de_idempotencia_distingue_version_y_canal() {
        var cambio = new CambioEstadoGuia(UUID.randomUUID(), "TCC123", EstadoGuia.EN_REPARTO, EstadoGuia.ENTREGADA, EPOCA, 7);

        assertThat(PoliticaNotificacion.claveIdempotencia(cambio, CanalNotificacion.SMS)).isEqualTo("TCC123:7:SMS");
        assertThat(PoliticaNotificacion.claveIdempotencia(cambio, CanalNotificacion.CORREO)).isEqualTo("TCC123:7:CORREO");
    }

    @Test
    void el_correo_es_el_alterno_del_sms_y_no_tiene_alterno() {
        assertThat(PoliticaNotificacion.canalAlterno(CanalNotificacion.SMS)).contains(CanalNotificacion.CORREO);
        assertThat(PoliticaNotificacion.canalAlterno(CanalNotificacion.CORREO)).isEmpty();
    }
}
