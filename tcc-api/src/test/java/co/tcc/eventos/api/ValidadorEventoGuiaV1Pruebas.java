package co.tcc.eventos.api;

import static org.assertj.core.api.Assertions.assertThat;

import co.tcc.eventos.api.validacion.ValidadorEventoGuiaV1;
import co.tcc.eventos.contratos.v1.EventoGuiaV1;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ValidadorEventoGuiaV1Pruebas {

    private static final Instant AHORA = Instant.parse("2026-11-30T15:00:00Z");
    private final ValidadorEventoGuiaV1 validador = new ValidadorEventoGuiaV1(Clock.fixed(AHORA, ZoneOffset.UTC));

    private static EventoGuiaV1 valido() {
        return new EventoGuiaV1(UUID.randomUUID(), "TCC123456789", "EN_REPARTO",
                OffsetDateTime.ofInstant(AHORA, ZoneOffset.ofHours(-5)).minusMinutes(1), "TMS", null);
    }

    private static EventoGuiaV1 con(String numeroGuia, String estado, OffsetDateTime ocurridoEn, String novedad) {
        var v = valido();
        return new EventoGuiaV1(v.idEvento(), numeroGuia, estado, ocurridoEn, v.origen(), novedad);
    }

    @Test
    void un_evento_valido_no_tiene_errores() {
        assertThat(validador.validar(valido())).isEmpty();
    }

    @Test
    void rechaza_id_de_evento_vacio() {
        var v = valido();
        var sinId = new EventoGuiaV1(new UUID(0, 0), v.numeroGuia(), v.estado(), v.ocurridoEn(), v.origen(), null);

        assertThat(validador.validar(sinId)).containsKey("idEvento");
    }

    @ParameterizedTest
    @ValueSource(strings = {"TCC-123", "TCC 123", "TCC1234567890123456789012345678901", "TCCñ1"})
    void rechaza_numeros_de_guia_mal_formados(String numero) {
        assertThat(validador.validar(con(numero, "EN_REPARTO", valido().ocurridoEn(), null))).containsKey("numeroGuia");
    }

    @Test
    void rechaza_estados_que_no_estan_en_el_contrato() {
        assertThat(validador.validar(con("TCC1", "PERDIDA", valido().ocurridoEn(), null))).containsKey("estado");
    }

    @Test
    void rechaza_eventos_en_el_futuro_mas_alla_de_la_tolerancia() {
        var futuro = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC).plusMinutes(6);
        assertThat(validador.validar(con("TCC1", "EN_REPARTO", futuro, null))).containsKey("ocurridoEn");
    }

    @Test
    void acepta_desfase_de_reloj_dentro_de_la_tolerancia() {
        var casiFuturo = OffsetDateTime.ofInstant(AHORA, ZoneOffset.UTC).plusMinutes(4);
        assertThat(validador.validar(con("TCC1", "EN_REPARTO", casiFuturo, null))).isEmpty();
    }

    @Test
    void una_novedad_exige_descripcion() {
        assertThat(validador.validar(con("TCC1", "NOVEDAD", valido().ocurridoEn(), null))).containsKey("novedad");
    }

    @Test
    void reporta_todos_los_errores_a_la_vez() {
        var todoMal = new EventoGuiaV1(null, "", "", null, "", null);

        assertThat(validador.validar(todoMal)).containsOnlyKeys("idEvento", "numeroGuia", "estado", "ocurridoEn", "origen");
    }
}
