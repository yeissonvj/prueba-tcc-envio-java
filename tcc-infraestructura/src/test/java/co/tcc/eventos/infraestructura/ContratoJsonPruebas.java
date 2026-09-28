package co.tcc.eventos.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import co.tcc.eventos.contratos.v1.EstadoGuiaCambiadoV1;
import co.tcc.eventos.contratos.v1.EventoGuiaV1;
import co.tcc.eventos.contratos.v1.NotificacionPendienteV1;
import co.tcc.eventos.infraestructura.mapeo.JsonContratos;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;

/**
 * .NET y Java comparten los tópicos: lo que escribe uno lo tiene que leer el otro.
 * Los JSON de referencia son los que produce la versión .NET (System.Text.Json, JsonSerializerDefaults.Web).
 */
class ContratoJsonPruebas {

    private static final UUID ID = UUID.fromString("0199a1b2-7c3d-7e4f-8a9b-0c1d2e3f4a5b");
    private static final OffsetDateTime OCURRIDO = OffsetDateTime.of(2026, 11, 30, 10, 15, 0, 0, ZoneOffset.ofHours(-5));

    private static final String EVENTO_DOTNET = """
            {"idEvento":"0199a1b2-7c3d-7e4f-8a9b-0c1d2e3f4a5b","numeroGuia":"TCC123456789","estado":"EN_REPARTO",\
            "ocurridoEn":"2026-11-30T10:15:00-05:00","origen":"TMS","novedad":null}""";

    private static final String CAMBIO_DOTNET = """
            {"idEvento":"0199a1b2-7c3d-7e4f-8a9b-0c1d2e3f4a5b","numeroGuia":"TCC123456789","estadoAnterior":null,\
            "estadoNuevo":"CREADA","ocurridoEn":"2026-11-30T10:15:00-05:00","origen":"TMS","novedad":null,"version":1}""";

    // .NET escribe DateTimeOffset con 7 decimales en los mensajes de reintento ("O").
    private static final String PENDIENTE_DOTNET = """
            {"cambio":%s,"canal":"SMS","intento":2,"recibidoEn":"2026-11-30T15:15:00.1234567+00:00"}""".formatted(CAMBIO_DOTNET);

    @Test
    void lee_el_evento_que_publica_la_version_dotnet() {
        var evento = JsonContratos.leer(EVENTO_DOTNET, EventoGuiaV1.class);

        assertThat(evento).isEqualTo(new EventoGuiaV1(ID, "TCC123456789", "EN_REPARTO", OCURRIDO, "TMS", null));
        assertThat(evento.ocurridoEn().getOffset()).isEqualTo(ZoneOffset.ofHours(-5));
    }

    @Test
    void escribe_el_evento_igual_que_la_version_dotnet() {
        var json = JsonContratos.escribir(new EventoGuiaV1(ID, "TCC123456789", "EN_REPARTO", OCURRIDO, "TMS", null));

        assertThat(arbol(json)).isEqualTo(arbol(EVENTO_DOTNET));
    }

    @Test
    void el_cambio_de_estado_va_y_vuelve_sin_perder_nada() {
        var cambio = JsonContratos.leer(CAMBIO_DOTNET, EstadoGuiaCambiadoV1.class);

        assertThat(cambio.estadoAnterior()).isNull();
        assertThat(cambio.version()).isEqualTo(1);
        assertThat(arbol(JsonContratos.escribir(cambio))).isEqualTo(arbol(CAMBIO_DOTNET));
    }

    @Test
    void lee_los_reintentos_de_notificacion_de_la_version_dotnet() {
        var pendiente = JsonContratos.leer(PENDIENTE_DOTNET, NotificacionPendienteV1.class);

        assertThat(pendiente.intento()).isEqualTo(2);
        assertThat(pendiente.cambio().numeroGuia()).isEqualTo("TCC123456789");
        assertThat(pendiente.recibidoEn()).isAtSameInstantAs(OffsetDateTime.of(2026, 11, 30, 15, 15, 0, 123_456_700, ZoneOffset.UTC));
    }

    @Test
    void un_mensaje_anterior_sin_recibidoEn_sigue_siendo_valido() {
        var pendiente = JsonContratos.leer("{\"cambio\":%s,\"canal\":\"CORREO\",\"intento\":0}".formatted(CAMBIO_DOTNET),
                NotificacionPendienteV1.class);

        assertThat(pendiente.recibidoEn()).isNull();
    }

    @Test
    void un_campo_nuevo_opcional_no_rompe_a_los_consumidores() {
        var conCampoNuevo = EVENTO_DOTNET.replace("}", ",\"campoFuturo\":\"x\"}");

        assertThat(JsonContratos.leer(conCampoNuevo, EventoGuiaV1.class).idEvento()).isEqualTo(ID);
    }

    @Test
    void un_json_mal_formado_se_rechaza() {
        assertThatThrownBy(() -> JsonContratos.leer("{roto", EventoGuiaV1.class)).isInstanceOf(JacksonException.class);
    }

    private static JsonNode arbol(String json) {
        return JsonContratos.MAPPER.readTree(json);
    }
}
